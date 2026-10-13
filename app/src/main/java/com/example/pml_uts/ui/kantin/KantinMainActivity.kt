package com.example.pml_uts.ui.kantin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.example.pml_uts.R
import com.example.pml_uts.data.local.MyFinDatabaseHelper
import com.example.pml_uts.data.local.SessionManager
import com.example.pml_uts.data.model.KantinDashboardResponse
import com.example.pml_uts.data.model.Menu as MenuModel
import com.example.pml_uts.data.remote.ApiClient
import com.example.pml_uts.databinding.ActivityKantinMainBinding
import com.example.pml_uts.ui.LoginActivity
import com.example.pml_uts.util.NotificationSoundUtil
import com.google.gson.reflect.TypeToken
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.NumberFormat
import java.util.Locale

class KantinMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKantinMainBinding
    private lateinit var apiClient: ApiClient
    private lateinit var sessionManager: SessionManager
    private lateinit var dbHelper: MyFinDatabaseHelper

    private lateinit var menuAdapter: KantinMenuAdapter
    private lateinit var trxAdapter: KantinTransactionAdapter

    private var allMenus: MutableList<MenuModel> = mutableListOf()
    private val bankList = arrayOf("Bank Central Asia (BCA)", "Bank Rakyat Indonesia (BRI)", "Bank Negara Indonesia (BNI)", "Bank Mandiri", "Bank Syariah Indonesia (BSI)", "Bank Jatim")

    // State untuk foto di dialog menu (Langsung ke Server Web)
    private var activeImgPreview: ImageView? = null
    private var activeCardPreview: View? = null
    private var activeTvStatus: TextView? = null
    private var currentPhotoBase64: String? = null

    // Launcher Kamera (Buka kamera native & ambil foto produk)
    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            activeCardPreview?.visibility = View.VISIBLE
            activeImgPreview?.visibility = View.VISIBLE
            activeImgPreview?.setImageBitmap(bitmap)
            processBitmapToBase64(bitmap)
        }
    }

    // Launcher Galeri (Pilih foto produk dari galeri perangkat)
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            activeCardPreview?.visibility = View.VISIBLE
            activeImgPreview?.visibility = View.VISIBLE
            activeImgPreview?.setImageURI(uri)
            processUriToBase64(uri)
        }
    }

    // Launcher Izin Kamera
    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            takePhotoLauncher.launch(null)
        } else {
            Toast.makeText(this, "Izin kamera diperlukan untuk mengambil foto menu", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKantinMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarKantin)
        apiClient = ApiClient(this)
        sessionManager = SessionManager(this)
        dbHelper = MyFinDatabaseHelper(this)

        setupRecyclerViews()
        setupBottomNav()
        setupBankSpinner()
        setupListeners()

        loadDashboard()
        loadMenus()
        loadRekening()
    }

    private fun setupRecyclerViews() {
        binding.rvKantinMenuList.layoutManager = LinearLayoutManager(this)
        menuAdapter = KantinMenuAdapter(
            list = emptyList(),
            onEdit = { menu -> showEditMenuDialog(menu) },
            onDelete = { menu -> showDeleteMenuDialog(menu) }
        )
        binding.rvKantinMenuList.adapter = menuAdapter

        binding.rvKantinRecentTrx.layoutManager = LinearLayoutManager(this)
        trxAdapter = KantinTransactionAdapter(emptyList())
        binding.rvKantinRecentTrx.adapter = trxAdapter
    }

    private fun setupBottomNav() {
        binding.kantinBottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    binding.viewKantinDashboard.visibility = View.VISIBLE
                    binding.viewKantinMenu.visibility = View.GONE
                    binding.viewKantinRekening.visibility = View.GONE
                    loadDashboard()
                }
                R.id.nav_menu -> {
                    binding.viewKantinDashboard.visibility = View.GONE
                    binding.viewKantinMenu.visibility = View.VISIBLE
                    binding.viewKantinRekening.visibility = View.GONE
                    loadMenus()
                }
                R.id.nav_rekening -> {
                    binding.viewKantinDashboard.visibility = View.GONE
                    binding.viewKantinMenu.visibility = View.GONE
                    binding.viewKantinRekening.visibility = View.VISIBLE
                    loadRekening()
                }
            }
            true
        }
    }

    private fun setupBankSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, bankList)
        binding.spnKantinBank.adapter = adapter
    }

    private fun setupListeners() {
        binding.actvKantinMenuSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim().lowercase()
                val filtered = if (query.isEmpty()) allMenus else allMenus.filter {
                    it.namaMenu.lowercase().contains(query) || (it.kategori?.lowercase()?.contains(query) ?: false)
                }
                menuAdapter.updateData(filtered)
                binding.tvKantinMenuEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.fabAddMenu.setOnClickListener {
            showAddMenuDialog()
        }

        binding.tvKantinAutofillSandbox.setOnClickListener {
            val randomSuffix = (1000..9999).random()
            binding.edtKantinNoRek.setText("481111111111$randomSuffix")
            binding.edtKantinAtasNama.setText(sessionManager.getUserName())
            val bcaIdx = bankList.indexOfFirst { it.contains("BCA", ignoreCase = true) }
            if (bcaIdx != -1) binding.spnKantinBank.setSelection(bcaIdx)
            Toast.makeText(this, "Nomor rekening sandbox demo otomatis terisi!", Toast.LENGTH_SHORT).show()
        }

        binding.btnSaveRekeningKantin.setOnClickListener {
            saveRekening()
        }
    }

    private fun loadDashboard() {
        val userId = sessionManager.getUserId()
        apiClient.get("/kantin/dashboard?user_id=$userId") { success, response, _ ->
            runOnUiThread {
                if (success && response != null) {
                    try {
                        val dash = apiClient.gson.fromJson(response, KantinDashboardResponse::class.java)
                        if (dash.success) {
                            binding.tvKantinDashboardNama.text = dash.kantin?.namaKantin ?: sessionManager.getUserName()
                            binding.tvKantinDashboardStand.text = "Lokasi: ${dash.kantin?.noStand ?: "Stand Kantin"}"

                            val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
                            val rev = dash.summary?.totalRevenue ?: 0L
                            binding.tvKantinTotalRevenue.text = formatRupiah.format(rev).replace(",00", "")
                            binding.tvKantinTotalOrders.text = "${dash.summary?.totalTransactions ?: 0} Pesanan"

                            val status = dash.summary?.statusVerifikasi ?: "pending"
                            if (status == "approved") {
                                binding.tvKantinDashboardStatus.text = "Status: Approved (Aktif)"
                                binding.tvKantinDashboardStatus.setBackgroundColor(0xFFD1FAE5.toInt())
                                binding.tvKantinDashboardStatus.setTextColor(0xFF065F46.toInt())
                            } else {
                                binding.tvKantinDashboardStatus.text = "Status: Menunggu Verifikasi Admin"
                                binding.tvKantinDashboardStatus.setBackgroundColor(0xFFFEF3C7.toInt())
                                binding.tvKantinDashboardStatus.setTextColor(0xFF92400E.toInt())
                            }

                            trxAdapter.updateData(dash.recentTransactions)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun loadMenus() {
        val userId = sessionManager.getUserId()
        apiClient.get("/kantin/menu?user_id=$userId") { success, response, _ ->
            runOnUiThread {
                if (success && response != null) {
                    try {
                        val json = JSONObject(response)
                        val arrayStr = json.getJSONArray("data").toString()
                        val type = object : TypeToken<List<MenuModel>>() {}.type
                        val list: List<MenuModel> = apiClient.gson.fromJson(arrayStr, type)

                        allMenus.clear()
                        allMenus.addAll(list)
                        menuAdapter.updateData(allMenus)
                        binding.tvKantinMenuEmpty.visibility = if (allMenus.isEmpty()) View.VISIBLE else View.GONE

                        dbHelper.saveMenuCache(allMenus)

                        val suggestions = allMenus.map { it.namaMenu }
                        binding.actvKantinMenuSearch.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, suggestions))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    val cached = dbHelper.getCachedMenus()
                    if (cached.isNotEmpty()) {
                        allMenus.clear()
                        allMenus.addAll(cached)
                        menuAdapter.updateData(allMenus)
                        binding.tvKantinMenuEmpty.visibility = if (allMenus.isEmpty()) View.VISIBLE else View.GONE
                    } else {
                        binding.tvKantinMenuEmpty.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun showAddMenuDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_menu, null)
        val edtNama = dialogView.findViewById<EditText>(R.id.edtDialogNamaMenu)
        val edtHarga = dialogView.findViewById<EditText>(R.id.edtDialogHarga)
        val edtStock = dialogView.findViewById<EditText>(R.id.edtDialogStock)
        val rbMakanan = dialogView.findViewById<RadioButton>(R.id.rbMakanan)
        val btnCamera = dialogView.findViewById<View>(R.id.btnPickCamera)
        val btnGallery = dialogView.findViewById<View>(R.id.btnPickGallery)
        val cardPreview = dialogView.findViewById<View>(R.id.cardPreviewMenu)
        val imgPreview = dialogView.findViewById<ImageView>(R.id.imgPreviewMenu)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvUploadStatus)

        // Reset state upload foto
        currentPhotoBase64 = null
        activeImgPreview = imgPreview
        activeCardPreview = cardPreview
        activeTvStatus = tvStatus

        btnCamera.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                takePhotoLauncher.launch(null)
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        btnGallery.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Simpan Menu") { _, _ ->
                val nama = edtNama.text.toString().trim()
                val hargaStr = edtHarga.text.toString().trim()
                val stockStr = edtStock.text.toString().trim()
                val kategori = if (rbMakanan.isChecked) "makanan" else "minuman"

                if (nama.isEmpty() || hargaStr.isEmpty() || stockStr.isEmpty()) {
                    Toast.makeText(this, "Nama, harga, dan stok wajib diisi!", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val json = JSONObject().apply {
                    put("user_id", sessionManager.getUserId())
                    put("nama_menu", nama)
                    put("harga", hargaStr.toDoubleOrNull() ?: 0.0)
                    put("stock", stockStr.toIntOrNull() ?: 0)
                    put("kategori", kategori)
                    if (!currentPhotoBase64.isNullOrEmpty()) {
                        put("foto_base64", currentPhotoBase64)
                    }
                }.toString()

                apiClient.post("/kantin/menu", json) { success, _, err ->
                    runOnUiThread {
                        if (success) {
                            NotificationSoundUtil.playSuccessBeep()
                            Toast.makeText(this, "Menu baru berhasil ditambahkan ke web & POS!", Toast.LENGTH_SHORT).show()
                            loadMenus()
                        } else {
                            Toast.makeText(this, err ?: "Gagal menambah menu", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showEditMenuDialog(menu: MenuModel) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_menu, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val edtNama = dialogView.findViewById<EditText>(R.id.edtDialogNamaMenu)
        val edtHarga = dialogView.findViewById<EditText>(R.id.edtDialogHarga)
        val edtStock = dialogView.findViewById<EditText>(R.id.edtDialogStock)
        val rbMakanan = dialogView.findViewById<RadioButton>(R.id.rbMakanan)
        val rbMinuman = dialogView.findViewById<RadioButton>(R.id.rbMinuman)
        val btnCamera = dialogView.findViewById<View>(R.id.btnPickCamera)
        val btnGallery = dialogView.findViewById<View>(R.id.btnPickGallery)
        val cardPreview = dialogView.findViewById<View>(R.id.cardPreviewMenu)
        val imgPreview = dialogView.findViewById<ImageView>(R.id.imgPreviewMenu)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvUploadStatus)

        tvTitle.text = "Edit Menu Makanan / Minuman"
        edtNama.setText(menu.namaMenu)
        edtHarga.setText(menu.harga.toInt().toString())
        edtStock.setText(menu.stock.toString())

        if (menu.kategori == "minuman") {
            rbMinuman.isChecked = true
        } else {
            rbMakanan.isChecked = true
        }

        // Tampilkan foto eksisting dari web
        currentPhotoBase64 = null
        activeImgPreview = imgPreview
        activeCardPreview = cardPreview
        activeTvStatus = tvStatus

        val rawPhoto = menu.fotoUrl ?: menu.fotoMenu
        if (!rawPhoto.isNullOrEmpty()) {
            val fullPhotoUrl = if (rawPhoto.startsWith("http")) rawPhoto else "https://myfin.web.id/storage/$rawPhoto"
            cardPreview.visibility = View.VISIBLE
            imgPreview.visibility = View.VISIBLE
            imgPreview.load(fullPhotoUrl)
            tvStatus.text = "Foto menu saat ini aktif dari web"
        }

        btnCamera.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                takePhotoLauncher.launch(null)
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        btnGallery.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Perbarui") { _, _ ->
                val nama = edtNama.text.toString().trim()
                val hargaStr = edtHarga.text.toString().trim()
                val stockStr = edtStock.text.toString().trim()
                val kategori = if (rbMakanan.isChecked) "makanan" else "minuman"

                val json = JSONObject().apply {
                    put("nama_menu", nama)
                    put("harga", hargaStr.toDoubleOrNull() ?: menu.harga)
                    put("stock", stockStr.toIntOrNull() ?: menu.stock)
                    put("kategori", kategori)
                    if (!currentPhotoBase64.isNullOrEmpty()) {
                        put("foto_base64", currentPhotoBase64)
                    }
                }.toString()

                apiClient.post("/kantin/menu/${menu.id}/update", json) { success, _, err ->
                    runOnUiThread {
                        if (success) {
                            NotificationSoundUtil.playSuccessBeep()
                            Toast.makeText(this, "Menu berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                            loadMenus()
                        } else {
                            Toast.makeText(this, err ?: "Gagal memperbarui menu", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun processBitmapToBase64(bitmap: Bitmap) {
        try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            val byteArray = stream.toByteArray()
            currentPhotoBase64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
            activeTvStatus?.text = "Foto siap disimpan ke server web"
            activeTvStatus?.setTextColor(0xFF059669.toInt())
            Toast.makeText(this, "Foto produk berhasil dipilih!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal memproses foto: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processUriToBase64(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                processBitmapToBase64(bitmap)
            } else {
                Toast.makeText(this, "Gagal memuat gambar dari galeri", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membaca galeri: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteMenuDialog(menu: MenuModel) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Menu")
            .setMessage("Hapus menu ${menu.namaMenu} dari katalog?")
            .setPositiveButton("Hapus") { _, _ ->
                apiClient.delete("/kantin/menu/${menu.id}") { success, _, err ->
                    runOnUiThread {
                        if (success) {
                            NotificationSoundUtil.playSuccessBeep()
                            Toast.makeText(this, "Menu dihapus", Toast.LENGTH_SHORT).show()
                            loadMenus()
                        } else {
                            Toast.makeText(this, err ?: "Gagal menghapus", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun loadRekening() {
        val userId = sessionManager.getUserId()
        apiClient.get("/kantin/rekening?user_id=$userId") { success, response, _ ->
            runOnUiThread {
                if (success && response != null) {
                    try {
                        val json = JSONObject(response)
                        if (json.has("rekening") && !json.isNull("rekening")) {
                            val rek = json.getJSONObject("rekening")
                            val bank = rek.optString("bank_name")
                            val noRek = rek.optString("account_number")
                            val an = rek.optString("account_holder_name")

                            binding.edtKantinNoRek.setText(noRek)
                            binding.edtKantinAtasNama.setText(an)

                            val idx = bankList.indexOfFirst { it.contains(bank, ignoreCase = true) }
                            if (idx != -1) {
                                binding.spnKantinBank.setSelection(idx)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun saveRekening() {
        val bankName = binding.spnKantinBank.selectedItem.toString()
        val noRek = binding.edtKantinNoRek.text.toString().trim()
        val an = binding.edtKantinAtasNama.text.toString().trim()

        if (noRek.isEmpty() || an.isEmpty()) {
            Toast.makeText(this, "Nomor rekening dan atas nama wajib diisi!", Toast.LENGTH_SHORT).show()
            return
        }

        val json = JSONObject().apply {
            put("user_id", sessionManager.getUserId())
            put("bank_name", bankName)
            put("account_number", noRek)
            put("account_holder_name", an)
        }.toString()

        apiClient.post("/kantin/rekening", json) { success, _, err ->
            runOnUiThread {
                if (success) {
                    NotificationSoundUtil.playSuccessBeep()
                    Toast.makeText(this, "Rekening pencairan berhasil disimpan!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, err ?: "Gagal menyimpan rekening", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_options_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                loadDashboard()
                loadMenus()
                loadRekening()
                Toast.makeText(this, "Data diperbarui dari server", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_about -> {
                AlertDialog.Builder(this)
                    .setTitle("Tentang MyFin")
                    .setMessage("Portal Manajemen Stand Kantin MyFin\nSistem Finansial & POS Mandiri Terintegrasi")
                    .setPositiveButton("OK", null)
                    .show()
                true
            }
            R.id.action_logout -> {
                sessionManager.logout()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}