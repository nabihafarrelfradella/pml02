package com.example.pml_uts.ui.kantin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.example.pml_uts.util.FormatUtil
import com.example.pml_uts.util.SupabaseStorageHelper
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
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

    private val realTimeHandler = Handler(Looper.getMainLooper())
    private val realTimeRunnable = object : Runnable {
        override fun run() {
            loadDashboard()
            loadRekening()
            realTimeHandler.postDelayed(this, 5000)
        }
    }

    override fun onResume() {
        super.onResume()
        loadDashboard()
        loadMenus()
        loadRekening()
        realTimeHandler.removeCallbacks(realTimeRunnable)
        realTimeHandler.postDelayed(realTimeRunnable, 5000)
    }

    override fun onPause() {
        super.onPause()
        realTimeHandler.removeCallbacks(realTimeRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        realTimeHandler.removeCallbacks(realTimeRunnable)
    }


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
    private var currentPhotoUrl: String? = null
    private var isProcessingPhoto = false
    private var photoRequestId = 0

    // Launcher Kamera (Buka kamera native & ambil foto produk)
    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            activeCardPreview?.visibility = View.VISIBLE
            activeImgPreview?.visibility = View.VISIBLE
            processBitmapToBase64(bitmap)
        }
    }

    // Launcher Galeri (Pilih foto produk dari galeri perangkat)
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            activeCardPreview?.visibility = View.VISIBLE
            activeImgPreview?.visibility = View.VISIBLE
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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityKantinMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.toolbarKantin.updateLayoutParams { height = ViewGroup.LayoutParams.WRAP_CONTENT }
            binding.toolbarKantin.updatePadding(top = insets.top)
            binding.kantinBottomNav.updatePadding(bottom = insets.bottom)
            windowInsets
        }

        setSupportActionBar(binding.toolbarKantin)
        apiClient = ApiClient(this)
        sessionManager = SessionManager(this)
        dbHelper = MyFinDatabaseHelper(this)

        setupRecyclerViews()
        setupBottomNav()
        setupBankSpinner()
        setupListeners()
        updateCardVisualLive()

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
        trxAdapter = KantinTransactionAdapter(
            list = emptyList(),
            onItemClick = { trx -> showTransactionDetailDialog(trx) },
            onItemLongClick = { trx -> showTransactionDetailDialog(trx) }
        )
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
        binding.spnKantinBank.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateCardVisualLive()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
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

                            val kantinPhoto = FormatUtil.resolveImageUrl(dash.kantin?.user?.photoUrl ?: dash.kantin?.user?.photo)
                            if (!kantinPhoto.isNullOrEmpty()) {
                                binding.imgKantinProfile.load(kantinPhoto) {
                                    crossfade(true)
                                    placeholder(android.R.drawable.ic_menu_agenda)
                                    error(android.R.drawable.ic_menu_agenda)
                                }
                            }

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
        photoRequestId++
        currentPhotoBase64 = null
        currentPhotoUrl = null
        isProcessingPhoto = false
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

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Simpan Menu", null)
            .setNegativeButton("Batal", null)
            .create()

        dialog.setOnDismissListener {
            photoRequestId++
            isProcessingPhoto = false
        }
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (isProcessingPhoto) {
                Toast.makeText(this, "Memproses foto...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val nama = edtNama.text.toString().trim()
            val hargaStr = edtHarga.text.toString().trim()
            val stockStr = edtStock.text.toString().trim()
            val kategori = if (rbMakanan.isChecked) "makanan" else "minuman"

            if (nama.isEmpty()) {
                edtNama.error = "Nama menu tidak boleh kosong"
                edtNama.requestFocus()
                return@setOnClickListener
            }

            if (hargaStr.isEmpty()) {
                edtHarga.error = "Harga wajib diisi"
                edtHarga.requestFocus()
                return@setOnClickListener
            }

            val harga = hargaStr.toLongOrNull()
            if (harga == null || harga <= 0) {
                edtHarga.error = "Harga harus berupa angka bulat lebih dari 0"
                edtHarga.requestFocus()
                return@setOnClickListener
            }

            if (stockStr.isEmpty()) {
                edtStock.error = "Stok wajib diisi"
                edtStock.requestFocus()
                return@setOnClickListener
            }

            val stock = stockStr.toIntOrNull()
            if (stock == null || stock < 0) {
                edtStock.error = "Stok harus berupa angka bulat minimal 0"
                edtStock.requestFocus()
                return@setOnClickListener
            }

            dialog.dismiss()

            val json = JSONObject().apply {
                put("user_id", sessionManager.getUserId())
                put("nama_menu", nama)
                put("harga", harga.toDouble())
                put("stock", stock)
                put("kategori", kategori)
                                if (!currentPhotoUrl.isNullOrEmpty()) {
                    put("foto_url", currentPhotoUrl)
                }
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
        photoRequestId++
        currentPhotoBase64 = null
        currentPhotoUrl = null
        isProcessingPhoto = false
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

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Perbarui", null)
            .setNegativeButton("Batal", null)
            .create()

        dialog.setOnDismissListener {
            photoRequestId++
            isProcessingPhoto = false
        }
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (isProcessingPhoto) {
                Toast.makeText(this, "Memproses foto...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val nama = edtNama.text.toString().trim()
            val hargaStr = edtHarga.text.toString().trim()
            val stockStr = edtStock.text.toString().trim()
            val kategori = if (rbMakanan.isChecked) "makanan" else "minuman"

            if (nama.isEmpty()) {
                edtNama.error = "Nama menu tidak boleh kosong"
                edtNama.requestFocus()
                return@setOnClickListener
            }

            if (hargaStr.isEmpty()) {
                edtHarga.error = "Harga wajib diisi"
                edtHarga.requestFocus()
                return@setOnClickListener
            }

            val harga = hargaStr.toLongOrNull()
            if (harga == null || harga <= 0) {
                edtHarga.error = "Harga harus berupa angka bulat lebih dari 0"
                edtHarga.requestFocus()
                return@setOnClickListener
            }

            if (stockStr.isEmpty()) {
                edtStock.error = "Stok wajib diisi"
                edtStock.requestFocus()
                return@setOnClickListener
            }

            val stock = stockStr.toIntOrNull()
            if (stock == null || stock < 0) {
                edtStock.error = "Stok harus berupa angka bulat minimal 0"
                edtStock.requestFocus()
                return@setOnClickListener
            }

            dialog.dismiss()

            val json = JSONObject().apply {
                put("nama_menu", nama)
                put("harga", harga.toDouble())
                put("stock", stock)
                put("kategori", kategori)
                                if (!currentPhotoUrl.isNullOrEmpty()) {
                    put("foto_url", currentPhotoUrl)
                }
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
    }

    private fun processBitmapToBase64(bitmap: Bitmap?) {
        val reqId = photoRequestId
        if (bitmap == null) {
            isProcessingPhoto = false
            Toast.makeText(this, "Foto tidak valid", Toast.LENGTH_SHORT).show()
            return
        }

        isProcessingPhoto = true
        activeTvStatus?.text = "Memproses foto..."
        activeTvStatus?.setTextColor(0xFF64748B.toInt())

        Thread {
            try {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
                val byteArray = stream.toByteArray()
                val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)

                runOnUiThread {
                    if (reqId == photoRequestId) {
                        currentPhotoBase64 = base64
                        isProcessingPhoto = false
                        activeImgPreview?.setImageBitmap(bitmap)
                        activeTvStatus?.text = "Foto siap disimpan"
                        activeTvStatus?.setTextColor(0xFF059669.toInt())
                        Toast.makeText(this@KantinMainActivity, "Foto produk siap!", Toast.LENGTH_SHORT).show()

                        // Bonus: upload ke Supabase di background (tidak memblokir simpan)
                        try {
                            val fileName = "menu_" + System.currentTimeMillis() + ".jpg"
                            SupabaseStorageHelper.uploadMenuPhoto(fileName, byteArray) { success, publicUrl, _ ->
                                if (reqId == photoRequestId && success && !publicUrl.isNullOrEmpty()) {
                                    currentPhotoUrl = publicUrl
                                }
                            }
                        } catch (_: Exception) { }
                    }
                }
            } catch (oom: OutOfMemoryError) {
                runOnUiThread {
                    if (reqId == photoRequestId) {
                        isProcessingPhoto = false
                        activeTvStatus?.text = "Memori tidak cukup"
                        activeTvStatus?.setTextColor(0xFFEF4444.toInt())
                        Toast.makeText(this, "Ukuran foto terlalu besar", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (reqId == photoRequestId) {
                        isProcessingPhoto = false
                        activeTvStatus?.text = "Gagal memproses foto"
                        activeTvStatus?.setTextColor(0xFFEF4444.toInt())
                        Toast.makeText(this, "Gagal memproses foto", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
    }

    private fun processUriToBase64(uri: Uri) {
        val reqId = photoRequestId
        isProcessingPhoto = true
        activeTvStatus?.text = "Memproses foto..."
        activeTvStatus?.setTextColor(0xFF64748B.toInt())

        Thread {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap != null) {
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
                    val byteArray = stream.toByteArray()
                    val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)

                    runOnUiThread {
                        if (reqId == photoRequestId) {
                            currentPhotoBase64 = base64
                            isProcessingPhoto = false
                            activeImgPreview?.setImageBitmap(bitmap)
                            activeTvStatus?.text = "Foto siap disimpan"
                            activeTvStatus?.setTextColor(0xFF059669.toInt())
                            Toast.makeText(this@KantinMainActivity, "Foto produk siap!", Toast.LENGTH_SHORT).show()

                            // Bonus: upload ke Supabase di background (tidak memblokir simpan)
                            try {
                                val fileName = "menu_" + System.currentTimeMillis() + ".jpg"
                                SupabaseStorageHelper.uploadMenuPhoto(fileName, byteArray) { success, publicUrl, _ ->
                                    if (reqId == photoRequestId && success && !publicUrl.isNullOrEmpty()) {
                                        currentPhotoUrl = publicUrl
                                    }
                                }
                            } catch (_: Exception) { }
                        }
                    }
                } else {
                    runOnUiThread {
                        if (reqId == photoRequestId) {
                            isProcessingPhoto = false
                            activeTvStatus?.text = "Gagal memuat gambar"
                            activeTvStatus?.setTextColor(0xFFEF4444.toInt())
                            Toast.makeText(this, "Gagal memuat gambar dari galeri", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (oom: OutOfMemoryError) {
                runOnUiThread {
                    if (reqId == photoRequestId) {
                        isProcessingPhoto = false
                        activeTvStatus?.text = "Memori tidak cukup"
                        activeTvStatus?.setTextColor(0xFFEF4444.toInt())
                        Toast.makeText(this, "Ukuran foto terlalu besar", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (reqId == photoRequestId) {
                        isProcessingPhoto = false
                        activeTvStatus?.text = "Gagal membaca galeri"
                        activeTvStatus?.setTextColor(0xFFEF4444.toInt())
                        Toast.makeText(this, "Gagal membaca galeri", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
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
                            updateCardVisualLive()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }


    private fun updateCardVisualLive() {
        val selectedBank = binding.spnKantinBank.selectedItem?.toString() ?: "Bank BCA"
        val rawNumber = binding.edtKantinNoRek.text?.toString()?.trim() ?: ""
        val holder = binding.edtKantinAtasNama.text?.toString()?.trim() ?: ""

        binding.tvKantinCardBankDisplay.text = if (selectedBank.isNotEmpty()) selectedBank else "Bank BCA"
        binding.tvKantinCardHolderDisplay.text = if (holder.isNotEmpty()) holder else (sessionManager.getUserName().ifEmpty { "Pemilik Stand" })

        binding.tvKantinCardNumberDisplay.text = if (rawNumber.isNotEmpty()) {
            if (rawNumber.length >= 8) {
                val first4 = rawNumber.take(4)
                val last4 = rawNumber.takeLast(4)
                "$first4 11** **** $last4"
            } else {
                rawNumber
            }
        } else {
            "4811 11** **** 1234"
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

    private fun showTransactionDetailDialog(trx: com.example.pml_uts.data.model.Transaction) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_transaction_detail, null)
        val tvTrxCode = dialogView.findViewById<TextView>(R.id.tvDetailTrxCode)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvDetailStatus)
        val tvStand = dialogView.findViewById<TextView>(R.id.tvDetailStand)
        val tvWaktu = dialogView.findViewById<TextView>(R.id.tvDetailWaktu)
        val layoutItems = dialogView.findViewById<android.widget.LinearLayout>(R.id.layoutDetailItems)
        val tvTotal = dialogView.findViewById<TextView>(R.id.tvDetailTotal)
        val btnClose = dialogView.findViewById<android.widget.Button>(R.id.btnDetailClose)

        tvTrxCode.text = trx.transactionCode
        tvStand.text = if (trx.student != null) "Pembeli: ${trx.student.name}" else "Stand Kantin"
        tvWaktu.text = "Waktu: " + FormatUtil.formatTrxDate(trx.createdAt)

        when (trx.status) {
            "success" -> {
                tvStatus.text = "Berhasil"
                tvStatus.setBackgroundColor(0xFFD1FAE5.toInt())
                tvStatus.setTextColor(0xFF065F46.toInt())
            }
            "pending" -> {
                tvStatus.text = "Menunggu"
                tvStatus.setBackgroundColor(0xFFFEF3C7.toInt())
                tvStatus.setTextColor(0xFF92400E.toInt())
            }
            else -> {
                tvStatus.text = "Gagal"
                tvStatus.setBackgroundColor(0xFFFEE2E2.toInt())
                tvStatus.setTextColor(0xFF991B1B.toInt())
            }
        }

        val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
        tvTotal.text = formatRupiah.format(trx.totalAmount).replace(",00", "")

        layoutItems.removeAllViews()
        val detailsList = trx.details ?: emptyList()
        if (detailsList.isNotEmpty()) {
            for (detail in detailsList) {
                val row = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 8, 0, 8)
                    }
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }

                val menuName = detail.menu?.namaMenu ?: "Menu Kantin"
                val tvItemDesc = TextView(this).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    text = "${detail.qty}x $menuName"
                    setTextColor(0xFF1E293B.toInt())
                    textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }

                val subtotalVal = if (detail.subtotal > 0) detail.subtotal else (detail.price * detail.qty)
                val tvItemSubtotal = TextView(this).apply {
                    text = formatRupiah.format(subtotalVal).replace(",00", "")
                    setTextColor(0xFF2563EB.toInt())
                    textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }

                row.addView(tvItemDesc)
                row.addView(tvItemSubtotal)
                layoutItems.addView(row)
            }
        } else {
            val tvEmptyItem = TextView(this).apply {
                text = "Pesanan: 1x Transaksi Penjualan Stand Kantin"
                setTextColor(0xFF475569.toInt())
                textSize = 13.5f
            }
            layoutItems.addView(tvEmptyItem)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
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