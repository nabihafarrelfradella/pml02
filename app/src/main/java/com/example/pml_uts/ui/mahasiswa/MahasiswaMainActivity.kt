package com.example.pml_uts.ui.mahasiswa

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pml_uts.R
import com.example.pml_uts.data.local.SessionManager
import com.example.pml_uts.data.model.MahasiswaDashboardResponse
import com.example.pml_uts.data.model.Transaction
import com.example.pml_uts.data.remote.ApiClient
import com.example.pml_uts.databinding.ActivityMahasiswaMainBinding
import com.example.pml_uts.ui.LoginActivity
import com.example.pml_uts.ui.kantin.KantinTransactionAdapter
import com.example.pml_uts.util.NotificationSoundUtil
import com.example.pml_uts.util.QrCodeUtil
import com.google.gson.reflect.TypeToken
import org.json.JSONObject
import java.util.Calendar

class MahasiswaMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMahasiswaMainBinding
    private lateinit var apiClient: ApiClient
    private lateinit var sessionManager: SessionManager

    private lateinit var recentTrxAdapter: KantinTransactionAdapter
    private lateinit var allRiwayatAdapter: KantinTransactionAdapter
    private var fullRiwayatList: List<Transaction> = emptyList()

    private val bankList = arrayOf("Bank Central Asia (BCA)", "Bank Rakyat Indonesia (BRI)", "Bank Negara Indonesia (BNI)", "Bank Mandiri", "Bank Syariah Indonesia (BSI)", "Bank Jatim")

    private val handler = Handler(Looper.getMainLooper())
    private var isPollingEnroll = false
    private var enrollDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMahasiswaMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.toolbarMhs.updateLayoutParams { height = ViewGroup.LayoutParams.WRAP_CONTENT }
            binding.toolbarMhs.updatePadding(top = insets.top)
            binding.mhsBottomNav.updatePadding(bottom = insets.bottom)
            windowInsets
        }

        setSupportActionBar(binding.toolbarMhs)
        apiClient = ApiClient(this)
        sessionManager = SessionManager(this)

        setupRecyclerViews()
        setupBottomNav()
        setupBankSpinner()
        setupListeners()
        setupQrCode()

        loadDashboard()
        loadRiwayat()
        checkIotDeviceStatus()
    }

    private fun setupRecyclerViews() {
        binding.rvMhsRecentTrx.layoutManager = LinearLayoutManager(this)
        recentTrxAdapter = KantinTransactionAdapter(emptyList())
        binding.rvMhsRecentTrx.adapter = recentTrxAdapter

        binding.rvMhsAllRiwayat.layoutManager = LinearLayoutManager(this)
        allRiwayatAdapter = KantinTransactionAdapter(emptyList())
        binding.rvMhsAllRiwayat.adapter = allRiwayatAdapter
    }

    private fun setupBottomNav() {
        binding.mhsBottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_mhs_dashboard -> {
                    showTab(0)
                    loadDashboard()
                }
                R.id.nav_mhs_pembayaran -> {
                    showTab(1)
                }
                R.id.nav_mhs_biometric -> {
                    showTab(2)
                    checkIotDeviceStatus()
                }
                R.id.nav_mhs_riwayat -> {
                    showTab(3)
                    loadRiwayat()
                }
            }
            true
        }
    }

    private fun showTab(index: Int) {
        binding.viewMhsDashboard.visibility = if (index == 0) View.VISIBLE else View.GONE
        binding.viewMhsPembayaran.visibility = if (index == 1) View.VISIBLE else View.GONE
        binding.viewMhsBiometric.visibility = if (index == 2) View.VISIBLE else View.GONE
        binding.viewMhsRiwayat.visibility = if (index == 3) View.VISIBLE else View.GONE
    }

    private fun setupBankSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, bankList)
        binding.spnMhsBank.adapter = adapter
    }

    private fun setupQrCode() {
        // QR-Code Generator (Poin 25 - 2%)
        val studentId = sessionManager.getUserId()
        val studentEmail = sessionManager.getUserEmail()
        val qrContent = "MYFIN:STUDENT:$studentId:$studentEmail"

        val qrBitmap = QrCodeUtil.generateQrBitmap(qrContent, 400)
        if (qrBitmap != null) {
            binding.imgMhsQrCode.setImageBitmap(qrBitmap)
            binding.tvMhsQrString.text = "Kode Kartu: $qrContent"
        }
    }

    private fun setupListeners() {
        binding.tvMhsAutofillSandbox.setOnClickListener {
            val randomSuffix = (1000..9999).random()
            binding.edtMhsCardNumber.setText("481111111111$randomSuffix")
            val bcaIdx = bankList.indexOfFirst { it.contains("BCA", ignoreCase = true) }
            if (bcaIdx != -1) binding.spnMhsBank.setSelection(bcaIdx)
            Toast.makeText(this, "Nomor kartu sandbox demo otomatis terisi!", Toast.LENGTH_SHORT).show()
        }
        // TRIGGER IOT REAL-TIME ENROLLMENT (Poin 28 - 10%)
        binding.btnTriggerIotEnroll.setOnClickListener {
            startIotEnrollment()
        }

        // Simpan Kartu Debit Midtrans
        binding.btnSaveMhsDebit.setOnClickListener {
            val bank = binding.spnMhsBank.selectedItem.toString()
            val cardNum = binding.edtMhsCardNumber.text.toString().trim()

            if (cardNum.length < 12) {
                Toast.makeText(this, "Masukkan nomor kartu yang valid (minimal 12 digit)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val json = JSONObject().apply {
                put("user_id", sessionManager.getUserId())
                put("bank_name", bank)
                put("card_number", cardNum)
            }.toString()

            apiClient.post("/mahasiswa/pembayaran", json) { success, _, err ->
                runOnUiThread {
                    if (success) {
                        NotificationSoundUtil.playSuccessBeep()
                        Toast.makeText(this, "Kartu Debit $bank berhasil ditautkan!", Toast.LENGTH_SHORT).show()
                        loadDashboard()
                    } else {
                        Toast.makeText(this, err ?: "Gagal menautkan kartu", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Simpan Sidik Jari Manual
        binding.btnSaveMhsBio.setOnClickListener {
            val slotIdStr = binding.edtMhsFingerprintId.text.toString().trim()
            val slotId = slotIdStr.toIntOrNull()

            if (slotId == null || slotId < 1 || slotId > 1000) {
                Toast.makeText(this, "Masukkan slot ID yang valid (1 - 1000)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val json = JSONObject().apply {
                put("user_id", sessionManager.getUserId())
                put("fingerprint_id", slotId)
            }.toString()

            apiClient.post("/mahasiswa/biometric", json) { success, _, err ->
                runOnUiThread {
                    if (success) {
                        NotificationSoundUtil.playSuccessBeep()
                        Toast.makeText(this, "Sidik jari AS608 (Slot #$slotId) berhasil disimpan!", Toast.LENGTH_SHORT).show()
                        loadDashboard()
                    } else {
                        Toast.makeText(this, err ?: "Gagal mendaftarkan sidik jari", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // DatePickerDialog Filter (Poin 6 - 1%)
        binding.btnFilterDate.setOnClickListener {
            val c = Calendar.getInstance()
            val year = c.get(Calendar.YEAR)
            val month = c.get(Calendar.MONTH)
            val day = c.get(Calendar.DAY_OF_MONTH)

            DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
                val dateStr = String.format("%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay)
                binding.btnFilterDate.text = "📅 Tanggal: $dateStr"

                val filtered = fullRiwayatList.filter { it.createdAt?.startsWith(dateStr) ?: false }
                allRiwayatAdapter.updateData(filtered)
                binding.tvMhsRiwayatEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
                Toast.makeText(this, "Memfilter transaksi tanggal $dateStr", Toast.LENGTH_SHORT).show()
            }, year, month, day).show()
        }

        binding.btnResetDateFilter.setOnClickListener {
            binding.btnFilterDate.text = "📅 Filter Tanggal (DatePicker)"
            allRiwayatAdapter.updateData(fullRiwayatList)
            binding.tvMhsRiwayatEmpty.visibility = if (fullRiwayatList.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    // ==========================================
    // IOT HARDWARE INTEGRATION (ESP32 + AS608)
    // ==========================================
    private fun checkIotDeviceStatus() {
        apiClient.get("/esp32/device-status") { success, response, _ ->
            runOnUiThread {
                if (success && response != null) {
                    try {
                        val json = JSONObject(response)
                        val status = json.optString("status", "Offline")
                        if (status.equals("Online", ignoreCase = true)) {
                            binding.tvIotStatusBadge.text = "ESP32: Online"
                            binding.tvIotStatusBadge.setBackgroundColor(0xFFD1FAE5.toInt())
                            binding.tvIotStatusBadge.setTextColor(0xFF065F46.toInt())
                        } else {
                            binding.tvIotStatusBadge.text = "ESP32: Standby"
                            binding.tvIotStatusBadge.setBackgroundColor(0xFFEFF6FF.toInt())
                            binding.tvIotStatusBadge.setTextColor(0xFF1E40AF.toInt())
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun startIotEnrollment() {
        val userId = sessionManager.getUserId()
        val json = JSONObject().apply {
            put("user_id", userId)
        }.toString()

        enrollDialog = AlertDialog.Builder(this)
            .setTitle("Sensor IoT ESP32 Aktif âš¡")
            .setMessage("Menginisialisasi modul scanner AS608...\n\nSilakan bersiap menempelkan jari Anda ke sensor sidik jari.")
            .setCancelable(false)
            .setNegativeButton("Batal") { dialog, _ ->
                isPollingEnroll = false
                dialog.dismiss()
            }
            .create()
        enrollDialog?.show()

        // Panggil endpoint start-enroll Laravel
        apiClient.post("/biometric/start-enroll", json) { success, response, err ->
            runOnUiThread {
                if (success) {
                    isPollingEnroll = true
                    enrollDialog?.setMessage("Sensor optik AS608 aktif!\n\nðŸ‘‰ Silakan tempelkan jari Anda pada sensor scanner sekarang...")
                    pollEnrollStatus()
                } else {
                    enrollDialog?.dismiss()
                    Toast.makeText(this, err ?: "Gagal mengaktifkan modul ESP32", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun pollEnrollStatus() {
        if (!isPollingEnroll) return

        handler.postDelayed({
            if (!isPollingEnroll) return@postDelayed

            apiClient.get("/biometric/enroll-status") { success, response, _ ->
                runOnUiThread {
                    if (!isPollingEnroll) return@runOnUiThread

                    if (success && response != null) {
                        try {
                            val json = JSONObject(response)
                            val status = json.optString("status")

                            when (status) {
                                "waiting_finger_2" -> {
                                    enrollDialog?.setMessage("Jari terdeteksi! âœ…\n\nðŸ‘‰ Angkat dan tempelkan jari Anda sekali lagi untuk konfirmasi...")
                                    pollEnrollStatus()
                                }
                                "success" -> {
                                    isPollingEnroll = false
                                    NotificationSoundUtil.playSuccessBeep()
                                    val slotId = json.optInt("fingerprint_id", 1)
                                    enrollDialog?.setTitle("Berhasil Terdaftar! ðŸŽ‰")
                                    enrollDialog?.setMessage("Sidik jari berhasil direkam ke modul sensor AS608 pada Slot #$slotId.\n\nAkun Anda kini siap digunakan belanja di kasir kantin.")
                                    enrollDialog?.setButton(AlertDialog.BUTTON_POSITIVE, "Selesai") { d, _ ->
                                        d.dismiss()
                                        loadDashboard()
                                    }
                                }
                                "error", "failed" -> {
                                    isPollingEnroll = false
                                    enrollDialog?.setTitle("Gagal Membaca")
                                    enrollDialog?.setMessage("Perekaman sidik jari gagal atau timeout. Silakan coba lagi.")
                                    enrollDialog?.setButton(AlertDialog.BUTTON_POSITIVE, "Tutup") { d, _ -> d.dismiss() }
                                }
                                else -> {
                                    // Masih menunggu jari
                                    pollEnrollStatus()
                                }
                            }
                        } catch (e: Exception) {
                            pollEnrollStatus()
                        }
                    } else {
                        pollEnrollStatus()
                    }
                }
            }
        }, 1500)
    }

    private fun loadDashboard() {
        val userId = sessionManager.getUserId()
        apiClient.get("/mahasiswa/dashboard?user_id=$userId") { success, response, _ ->
            runOnUiThread {
                if (success && response != null) {
                    try {
                        val dash = apiClient.gson.fromJson(response, MahasiswaDashboardResponse::class.java)
                        if (dash.success) {
                            binding.tvMhsDashboardName.text = dash.user?.name ?: sessionManager.getUserName()
                            binding.tvMhsDashboardEmail.text = dash.user?.email ?: sessionManager.getUserEmail()

                            if (dash.debit != null) {
                                binding.tvMhsDebitBadge.text = "Terhubung"
                                binding.tvMhsDebitBadge.setBackgroundColor(0xFFD1FAE5.toInt())
                                binding.tvMhsDebitBadge.setTextColor(0xFF065F46.toInt())
                                binding.tvMhsDebitInfo.text = "${dash.debit.bankName} (${dash.debit.cardTokenMasked ?: "****"})"
                            } else {
                                binding.tvMhsDebitBadge.text = "Belum Terhubung"
                                binding.tvMhsDebitBadge.setBackgroundColor(0xFFFEF3C7.toInt())
                                binding.tvMhsDebitBadge.setTextColor(0xFF92400E.toInt())
                                binding.tvMhsDebitInfo.text = "Tautkan kartu debit untuk aktivasi checkout POS."
                            }

                            if (dash.biometric != null) {
                                binding.tvMhsBioBadge.text = "Aktif"
                                binding.tvMhsBioBadge.setBackgroundColor(0xFFD1FAE5.toInt())
                                binding.tvMhsBioBadge.setTextColor(0xFF065F46.toInt())
                                binding.tvMhsBioInfo.text = "Slot Sensor AS608: Slot #${dash.biometric.fingerprintId}"
                            } else {
                                binding.tvMhsBioBadge.text = "Belum Terhubung"
                                binding.tvMhsBioBadge.setBackgroundColor(0xFFFEF3C7.toInt())
                                binding.tvMhsBioBadge.setTextColor(0xFF92400E.toInt())
                                binding.tvMhsBioInfo.text = "Tautkan sidik jari ke sensor AS608 untuk belanja tanpa kartu."
                            }

                            recentTrxAdapter.updateData(dash.recentTransactions)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun loadRiwayat() {
        val userId = sessionManager.getUserId()
        apiClient.get("/mahasiswa/riwayat?user_id=$userId") { success, response, _ ->
            runOnUiThread {
                if (success && response != null) {
                    try {
                        val json = JSONObject(response)
                        val arrayStr = json.getJSONArray("data").toString()
                        val type = object : TypeToken<List<Transaction>>() {}.type
                        fullRiwayatList = apiClient.gson.fromJson(arrayStr, type)
                        allRiwayatAdapter.updateData(fullRiwayatList)
                        binding.tvMhsRiwayatEmpty.visibility = if (fullRiwayatList.isEmpty()) View.VISIBLE else View.GONE
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isPollingEnroll = false
        handler.removeCallbacksAndMessages(null)
        enrollDialog?.dismiss()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_options_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                loadDashboard()
                loadRiwayat()
                checkIotDeviceStatus()
                Toast.makeText(this, "Data diperbarui", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_about -> {
                AlertDialog.Builder(this)
                    .setTitle("Tentang MyFin")
                    .setMessage("Portal Mahasiswa MyFin\nUTS Pemrograman Mobile Lanjut 2026/2027")
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