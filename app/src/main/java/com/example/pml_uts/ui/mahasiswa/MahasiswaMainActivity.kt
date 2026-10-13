package com.example.pml_uts.ui.mahasiswa

import android.app.DatePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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
import com.example.pml_uts.databinding.DialogBiometricEnrollBinding
import com.example.pml_uts.ui.LoginActivity
import com.example.pml_uts.ui.kantin.KantinTransactionAdapter
import coil.load
import com.example.pml_uts.util.FormatUtil
import com.google.android.material.datepicker.MaterialDatePicker
import java.util.Date
import java.util.TimeZone
import java.text.NumberFormat
import java.util.Locale
import com.example.pml_uts.util.NotificationSoundUtil
import com.google.gson.reflect.TypeToken
import org.json.JSONObject
import java.util.Calendar

class MahasiswaMainActivity : AppCompatActivity() {

    private val realTimeHandler = Handler(Looper.getMainLooper())
    private val realTimeRunnable = object : Runnable {
        override fun run() {
            if (!isPollingEnroll) {
                loadDashboard()
            }
            realTimeHandler.postDelayed(this, 4000)
        }
    }

    override fun onResume() {
        super.onResume()
        loadDashboard()
        loadRiwayat()
        realTimeHandler.removeCallbacks(realTimeRunnable)
        realTimeHandler.postDelayed(realTimeRunnable, 4000)
    }

    override fun onPause() {
        super.onPause()
        realTimeHandler.removeCallbacks(realTimeRunnable)
    }


    private lateinit var binding: ActivityMahasiswaMainBinding
    private lateinit var apiClient: ApiClient
    private lateinit var sessionManager: SessionManager

    private lateinit var recentTrxAdapter: KantinTransactionAdapter
    private lateinit var allRiwayatAdapter: KantinTransactionAdapter
    private var fullRiwayatList: List<Transaction> = emptyList()

    private val bankList = arrayOf(
        "Bank Central Asia (BCA)",
        "Bank Rakyat Indonesia (BRI)",
        "Bank Negara Indonesia (BNI)",
        "Bank Mandiri",
        "Bank Syariah Indonesia (BSI)",
        "Bank Jatim"
    )

    private val handler = Handler(Looper.getMainLooper())
    private var isPollingEnroll = false
    private var enrollDialog: AlertDialog? = null
    private var bioDialogBinding: DialogBiometricEnrollBinding? = null

    // Step text mapping identik dengan Web (dashboard.blade.php)
    private val stepTextMap = mapOf(
        "step1_wait" to Pair("Tempelkan Jari Anda", "Letakkan jari telunjuk rata pada sensor scanner meja kios."),
        "step1_process" to Pair("Membaca Sidik Jari...", "Tahan posisi jari sejenak pada kaca sensor..."),
        "step2_lift" to Pair("Angkat Jari Anda", "Lepaskan jari sejenak dari sensor scanner."),
        "step2_wait" to Pair("Tempelkan Jari Sekali Lagi", "Tempelkan jari yang sama untuk konfirmasi verifikasi."),
        "step2_process" to Pair("Menyimpan Model Biometrik...", "Sedang menyimpan model sidik jari ke sistem..."),
        "scan1" to Pair("Tempelkan Jari Anda", "Letakkan jari telunjuk rata pada sensor scanner meja kios."),
        "lift" to Pair("Angkat Jari Anda", "Lepaskan jari sejenak dari sensor scanner."),
        "scan2" to Pair("Tempelkan Jari Sekali Lagi", "Tempelkan jari yang sama untuk konfirmasi verifikasi."),
        "process" to Pair("Menyimpan Sidik Jari...", "Mohon tunggu sebentar..."),
        "retry" to Pair("Hasil Kurang Jelas, Ulangi", "Tempelkan kembali jari Anda dari awal.")
    )

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

        // Bind data nama dan identitas langsung dari session agar tidak kosong
        val currentName = sessionManager.getUserName()
        val currentEmail = sessionManager.getUserEmail()
        binding.tvMhsDashboardName.text = if (currentName.isNotEmpty()) currentName else "Mahasiswa"
        binding.tvMhsDashboardEmail.text = currentEmail
        binding.tvCardHolderDisplay.text = if (currentName.isNotEmpty()) currentName else "Mahasiswa"

        setupRecyclerViews()
        setupBottomNav()
        setupBankSpinner()
        setupListeners()

        loadDashboard()
        loadRiwayat()
    }

    private fun setupRecyclerViews() {
        binding.rvMhsRecentTrx.layoutManager = LinearLayoutManager(this)
        recentTrxAdapter = KantinTransactionAdapter(
            list = emptyList(),
            onItemClick = { trx -> showTransactionDetailDialog(trx) },
            onItemLongClick = { trx -> showTransactionDetailDialog(trx) }
        )
        binding.rvMhsRecentTrx.adapter = recentTrxAdapter

        binding.rvMhsAllRiwayat.layoutManager = LinearLayoutManager(this)
        allRiwayatAdapter = KantinTransactionAdapter(
            list = emptyList(),
            onItemClick = { trx -> showTransactionDetailDialog(trx) },
            onItemLongClick = { trx -> showTransactionDetailDialog(trx) }
        )
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
                    loadDashboard()
                }
                R.id.nav_mhs_biometric -> {
                    showTab(2)
                    loadDashboard()
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

        // Live preview bank ke visual kartu debit
        binding.spnMhsBank.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.tvCardBankDisplay.text = bankList[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun formatCardDisplay(raw: String): String {
        val digits = raw.replace("[^0-9]".toRegex(), "")
        if (digits.isEmpty()) return "4811 11** **** 1234"
        val sb = StringBuilder()
        for (i in digits.indices) {
            if (i > 0 && i % 4 == 0) sb.append(" ")
            sb.append(digits[i])
        }
        return sb.toString()
    }

    private fun setupListeners() {
        // Live preview nomor kartu saat diketik
        binding.edtMhsCardNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString().orEmpty()
                binding.tvCardNumberDisplay.text = formatCardDisplay(text)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Autofill Sandbox Demo (Persis Web)
        binding.tvMhsAutofillSandbox.setOnClickListener {
            val randomSuffix = (1000..9999).random()
            val demoCard = "481111111111" + randomSuffix
            binding.edtMhsCardNumber.setText(demoCard)
            binding.tvCardNumberDisplay.text = formatCardDisplay(demoCard)

            val bcaIdx = bankList.indexOfFirst { it.contains("BCA", ignoreCase = true) }
            if (bcaIdx != -1) {
                binding.spnMhsBank.setSelection(bcaIdx)
                binding.tvCardBankDisplay.text = bankList[bcaIdx]
            }
            Toast.makeText(this, "Nomor kartu sandbox demo otomatis terisi!", Toast.LENGTH_SHORT).show()
        }

        // Buka Modal Perekaman Sidik Jari dari Dashboard & Tab Sidik Jari
        binding.btnMhsRegisterBio.setOnClickListener {
            openBioEnrollModal()
        }
        binding.btnTabTriggerBioEnroll.setOnClickListener {
            openBioEnrollModal()
        }

        // Simpan Kartu Debit Midtrans (Gunakan route /mahasiswa/pembayaran)
        binding.btnSaveMhsDebit.setOnClickListener {
            val bank = binding.spnMhsBank.selectedItem.toString()
            val cardNum = binding.edtMhsCardNumber.text.toString().trim()

            if (cardNum.isEmpty()) {
                Toast.makeText(this, "Nomor kartu tidak boleh kosong", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val digitsOnly = cardNum.filter { it.isDigit() }
            if (digitsOnly.length < 8 && !cardNum.contains("*")) {
                Toast.makeText(this, "Nomor kartu minimal 16 digit angka", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val json = JSONObject().apply {
                put("user_id", sessionManager.getUserId())
                put("bank_name", bank)
                put("card_number", cardNum)
                if (cardNum.contains("*")) {
                    put("card_token_masked", cardNum)
                }
            }.toString()

            apiClient.post("/mahasiswa/pembayaran", json) { success, _, err ->
                runOnUiThread {
                    if (success) {
                        NotificationSoundUtil.playSuccessBeep()
                        Toast.makeText(this, "Kartu Debit Berhasil Disimpan!", Toast.LENGTH_SHORT).show()
                        loadDashboard()
                    } else {
                        Toast.makeText(this, err ?: "Gagal menyimpan kartu", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Filter Rentang Tanggal (Dari Kapan s/d Kapan)
        binding.btnFilterDate.setOnClickListener {
            pickDateRangeFilter()
        }

        binding.btnResetDateFilter.setOnClickListener {
            binding.btnFilterDate.text = "Pilih Rentang Tanggal"
            allRiwayatAdapter.updateData(fullRiwayatList)
            binding.tvMhsAllRiwayatEmpty.visibility = if (fullRiwayatList.isEmpty()) View.VISIBLE else View.GONE
            binding.tvMhsRiwayatEmpty.visibility = if (fullRiwayatList.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    // ==========================================
    // FILTER RENTANG TANGGAL (START DATE -> END DATE)
    // ==========================================
    private fun pickDateRangeFilter() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Pilih Rentang Tanggal")
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            val startMs = selection.first ?: return@addOnPositiveButtonClickListener
            val endMs = selection.second ?: return@addOnPositiveButtonClickListener

            val utcFormat = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val displayFormat = java.text.SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            val actualStart = utcFormat.format(Date(startMs))
            val actualEnd = utcFormat.format(Date(endMs))

            binding.btnFilterDate.text = "${displayFormat.format(Date(startMs))} - ${displayFormat.format(Date(endMs))}"

            val filtered = fullRiwayatList.filter { trx ->
                val dateOnly = trx.createdAt?.take(10) ?: ""
                dateOnly in actualStart..actualEnd
            }
            allRiwayatAdapter.updateData(filtered)
            binding.tvMhsAllRiwayatEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            Toast.makeText(this, "Menampilkan ${filtered.size} transaksi (${displayFormat.format(Date(startMs))} s/d ${displayFormat.format(Date(endMs))})", Toast.LENGTH_SHORT).show()
        }

        picker.show(supportFragmentManager, "MATERIAL_DATE_RANGE_PICKER")
    }

    private fun showTransactionDetailDialog(trx: Transaction) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_transaction_detail, null)
        val tvTrxCode = dialogView.findViewById<TextView>(R.id.tvDetailTrxCode)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvDetailStatus)
        val tvStand = dialogView.findViewById<TextView>(R.id.tvDetailStand)
        val tvWaktu = dialogView.findViewById<TextView>(R.id.tvDetailWaktu)
        val layoutItems = dialogView.findViewById<android.widget.LinearLayout>(R.id.layoutDetailItems)
        val tvTotal = dialogView.findViewById<TextView>(R.id.tvDetailTotal)
        val btnClose = dialogView.findViewById<android.widget.Button>(R.id.btnDetailClose)

        tvTrxCode.text = trx.transactionCode
        tvStand.text = "Stand: " + (trx.kantin?.namaKantin ?: "Stand Kantin")
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
                text = "Pesanan: 1x Transaksi Pembelian Stand Kantin"
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

    // ==========================================
    // MODAL PEREKAMAN SIDIK JARI REAL-TIME (WEB MATCH)
    // ==========================================
    private fun openBioEnrollModal() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_biometric_enroll, null)
        val dBinding = DialogBiometricEnrollBinding.bind(dialogView)
        bioDialogBinding = dBinding

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        enrollDialog = dialog

        dBinding.btnBioCancel.setOnClickListener {
            cancelBioEnrollment()
            dialog.dismiss()
        }

        dBinding.btnBioRetry.setOnClickListener {
            startRealtimeEnrollment()
        }

        dBinding.btnBioDone.setOnClickListener {
            dialog.dismiss()
            loadDashboard()
        }

        dialog.show()
        startRealtimeEnrollment()
    }

    private fun startRealtimeEnrollment() {
        val dBinding = bioDialogBinding ?: return
        isPollingEnroll = false

        dBinding.btnBioRetry.visibility = View.GONE
        dBinding.btnBioDone.visibility = View.GONE
        dBinding.btnBioCancel.visibility = View.VISIBLE
        dBinding.tvBioModalTitle.text = "Perekaman Sidik Jari"
        dBinding.tvBioModalSubtitle.text = "Menghubungkan ke pemindai biometrik di meja kasir..."

        // Icon waiting state
        dBinding.bioIconContainer.setBackgroundColor(0xFFEFF6FF.toInt())
        dBinding.bioIcon.setImageResource(android.R.drawable.ic_menu_camera)
        dBinding.bioIcon.imageTintList = ColorStateList.valueOf(0xFF2563EB.toInt())

        // Badge connecting
        dBinding.tvBioDeviceBadge.text = "Menghubungkan"
        dBinding.tvBioDeviceBadge.setBackgroundColor(0xFFE0F2FE.toInt())
        dBinding.tvBioDeviceBadge.setTextColor(0xFF0369A1.toInt())

        dBinding.tvBioStepText.text = "Mengirim perintah ke pemindai..."
        dBinding.tvBioHintText.text = "Pastikan alat pemindai menyala dan terhubung Wi-Fi."

        // Reset step pills
        dBinding.tvStepDot1.setBackgroundColor(0xFFEFF6FF.toInt())
        dBinding.tvStepDot1.setTextColor(0xFF2563EB.toInt())
        dBinding.tvStepDot2.setBackgroundColor(0xFFF8FAFC.toInt())
        dBinding.tvStepDot2.setTextColor(0xFF94A3B8.toInt())

        val json = JSONObject().apply {
            put("user_id", sessionManager.getUserId())
        }.toString()

        apiClient.post("/biometric/start-enroll", json) { success, response, err ->
            runOnUiThread {
                if (success && response != null) {
                    try {
                        val resJson = JSONObject(response)
                        if (resJson.optBoolean("success", true)) {
                            dBinding.tvBioModalSubtitle.text = "Silakan ikuti instruksi panduan di bawah ini pada scanner."
                            dBinding.tvBioStepText.text = "Menunggu alat pemindai merespons..."
                            dBinding.tvBioHintText.text = "Biasanya hanya butuh 1-2 detik."

                            isPollingEnroll = true
                            pollEnrollStatus()
                        } else {
                            val msg = resJson.optString("message", "Gagal memulai pendaftaran")
                            showEnrollFailed(msg)
                        }
                    } catch (e: Exception) {
                        showEnrollFailed("Format data tidak sesuai")
                    }
                } else {
                    showEnrollFailed(err ?: "Gagal menghubungi server pemindai")
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

                    val dBinding = bioDialogBinding ?: return@runOnUiThread

                    if (success && response != null) {
                        try {
                            val json = JSONObject(response)
                            val status = json.optString("status")

                            when (status) {
                                "scanning" -> {
                                    val step = json.optString("step", "step1_wait")
                                    val attempt = json.optInt("attempt", 1)

                                    dBinding.tvBioDeviceBadge.text = "Sensor Aktif"
                                    dBinding.tvBioDeviceBadge.setBackgroundColor(0xFFDCFCE7.toInt())
                                    dBinding.tvBioDeviceBadge.setTextColor(0xFF15803D.toInt())

                                    val texts = stepTextMap[step] ?: Pair("Tempelkan Jari Anda", "Letakkan jari telunjuk rata pada sensor scanner meja kios.")
                                    val attemptSuffix = if (attempt > 1) " (Percobaan " + attempt + "/3)" else ""
                                    dBinding.tvBioStepText.text = texts.first
                                    dBinding.tvBioHintText.text = texts.second + attemptSuffix

                                    updateStepDots(step)
                                    pollEnrollStatus()
                                }
                                "success" -> {
                                    isPollingEnroll = false
                                    NotificationSoundUtil.playSuccessBeep()

                                    dBinding.bioIconContainer.setBackgroundColor(0xFFDCFCE7.toInt())
                                    dBinding.bioIcon.setImageResource(android.R.drawable.checkbox_on_background)
                                    dBinding.bioIcon.imageTintList = ColorStateList.valueOf(0xFF16A34A.toInt())

                                    dBinding.tvBioModalTitle.text = "Pendaftaran Berhasil!"
                                    dBinding.tvBioModalSubtitle.text = "Sidik jari Anda telah tersimpan dan aktif."

                                    dBinding.tvBioDeviceBadge.text = "Berhasil"
                                    dBinding.tvBioDeviceBadge.setBackgroundColor(0xFFDCFCE7.toInt())
                                    dBinding.tvBioDeviceBadge.setTextColor(0xFF15803D.toInt())

                                    dBinding.tvBioStepText.text = "Sidik Jari Berhasil Disimpan"
                                    dBinding.tvBioHintText.text = "Sidik jari telah aktif untuk transaksi pembayaran di kasir."

                                    // Both dots green
                                    dBinding.tvStepDot1.setBackgroundColor(0xFFF0FDF4.toInt())
                                    dBinding.tvStepDot1.setTextColor(0xFF16A34A.toInt())
                                    dBinding.tvStepDot2.setBackgroundColor(0xFFF0FDF4.toInt())
                                    dBinding.tvStepDot2.setTextColor(0xFF16A34A.toInt())

                                    dBinding.btnBioCancel.visibility = View.GONE
                                    dBinding.btnBioRetry.visibility = View.GONE
                                    dBinding.btnBioDone.visibility = View.VISIBLE
                                }
                                "duplicate" -> {
                                    // DETEKSI DUPLIKAT PERSIS WEB (dashboard.blade.php)
                                    isPollingEnroll = false
                                    dBinding.bioIconContainer.setBackgroundColor(0xFFFEE2E2.toInt())
                                    dBinding.bioIcon.setImageResource(android.R.drawable.ic_delete)
                                    dBinding.bioIcon.imageTintList = ColorStateList.valueOf(0xFFDC2626.toInt())

                                    dBinding.tvBioModalTitle.text = "Duplikasi Sidik Jari Terdeteksi"
                                    dBinding.tvBioModalSubtitle.text = "Pendaftaran ditolak demi keamanan akun."

                                    dBinding.tvBioDeviceBadge.text = "Duplikasi Ditolak"
                                    dBinding.tvBioDeviceBadge.setBackgroundColor(0xFFFEE2E2.toInt())
                                    dBinding.tvBioDeviceBadge.setTextColor(0xFFB91C1C.toInt())

                                    val dupeMsg = json.optString("message", "Sidik jari ini sudah terdaftar pada akun lain!")
                                    dBinding.tvBioStepText.text = dupeMsg
                                    dBinding.tvBioHintText.text = "Satu sidik jari hanya dapat digunakan oleh satu mahasiswa. Silakan gunakan jari lain yang belum terdaftar."

                                    dBinding.btnBioRetry.text = "Gunakan Jari Lain"
                                    dBinding.btnBioRetry.visibility = View.VISIBLE
                                    dBinding.btnBioCancel.visibility = View.VISIBLE
                                    dBinding.btnBioDone.visibility = View.GONE
                                }
                                "failed", "error" -> {
                                    isPollingEnroll = false
                                    val msg = json.optString("message", "Perekaman sidik jari belum berhasil.")
                                    showEnrollFailed(msg)
                                }
                                else -> {
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
        }, 500)
    }

    private fun updateStepDots(step: String) {
        val dBinding = bioDialogBinding ?: return
        when (step) {
            "step1_wait", "step1_process", "scan1" -> {
                dBinding.tvStepDot1.setBackgroundColor(0xFFEFF6FF.toInt())
                dBinding.tvStepDot1.setTextColor(0xFF2563EB.toInt())
                dBinding.tvStepDot2.setBackgroundColor(0xFFF8FAFC.toInt())
                dBinding.tvStepDot2.setTextColor(0xFF94A3B8.toInt())
            }
            "step2_lift", "lift" -> {
                dBinding.tvStepDot1.setBackgroundColor(0xFFF0FDF4.toInt())
                dBinding.tvStepDot1.setTextColor(0xFF16A34A.toInt())
                dBinding.tvStepDot2.setBackgroundColor(0xFFFEFCE8.toInt())
                dBinding.tvStepDot2.setTextColor(0xFFCA8A04.toInt())
            }
            "step2_wait", "step2_process", "scan2" -> {
                dBinding.tvStepDot1.setBackgroundColor(0xFFF0FDF4.toInt())
                dBinding.tvStepDot1.setTextColor(0xFF16A34A.toInt())
                dBinding.tvStepDot2.setBackgroundColor(0xFFEFF6FF.toInt())
                dBinding.tvStepDot2.setTextColor(0xFF2563EB.toInt())
            }
        }
    }

    private fun showEnrollFailed(msg: String) {
        val dBinding = bioDialogBinding ?: return
        isPollingEnroll = false

        dBinding.bioIconContainer.setBackgroundColor(0xFFFEE2E2.toInt())
        dBinding.bioIcon.setImageResource(android.R.drawable.ic_delete)
        dBinding.bioIcon.imageTintList = ColorStateList.valueOf(0xFFDC2626.toInt())

        dBinding.tvBioModalTitle.text = "Perekaman Gagal"
        dBinding.tvBioModalSubtitle.text = "Sidik jari belum berhasil direkam."

        dBinding.tvBioDeviceBadge.text = "Gagal"
        dBinding.tvBioDeviceBadge.setBackgroundColor(0xFFFEE2E2.toInt())
        dBinding.tvBioDeviceBadge.setTextColor(0xFFB91C1C.toInt())

        dBinding.tvBioStepText.text = msg
        dBinding.tvBioHintText.text = "Pastikan jari bersih, kering, dan ditempel rata. Klik \"Coba Lagi\"."

        dBinding.btnBioRetry.text = "Coba Lagi"
        dBinding.btnBioRetry.visibility = View.VISIBLE
        dBinding.btnBioDone.visibility = View.GONE
    }

    private fun cancelBioEnrollment() {
        isPollingEnroll = false
        // Panggil endpoint cancel-enroll langsung ke root API
        apiClient.post("https://myfin.web.id/api/biometric/cancel-enroll", "{}") { _, _, _ -> }
    }

    private fun loadDashboard() {
        val userId = sessionManager.getUserId()
        apiClient.get("/mahasiswa/dashboard?user_id=" + userId) { success, response, _ ->
            runOnUiThread {
                if (success && response != null) {
                    try {
                        val dash = apiClient.gson.fromJson(response, MahasiswaDashboardResponse::class.java)
                        if (dash.success) {
                            val studentName = dash.user?.name ?: sessionManager.getUserName()
                            val studentEmail = dash.user?.email ?: sessionManager.getUserEmail()

                            binding.tvMhsDashboardName.text = studentName
                            binding.tvMhsDashboardEmail.text = studentEmail
                            binding.tvCardHolderDisplay.text = studentName

                            val studentPhoto = FormatUtil.resolveImageUrl(dash.user?.photoUrl ?: dash.user?.photo)
                            if (!studentPhoto.isNullOrEmpty()) {
                                binding.imgMhsProfile.load(studentPhoto) {
                                    crossfade(true)
                                    placeholder(android.R.drawable.ic_menu_myplaces)
                                    error(android.R.drawable.ic_menu_myplaces)
                                }
                            }

                            // Status Debit (Dashboard & Visual Card)
                            if (dash.debit != null) {
                                binding.tvMhsDebitBadge.text = "Terhubung"
                                binding.tvMhsDebitBadge.setBackgroundColor(0xFFD1FAE5.toInt())
                                binding.tvMhsDebitBadge.setTextColor(0xFF065F46.toInt())
                                binding.tvMhsDebitInfo.text = dash.debit.bankName + " (" + (dash.debit.cardTokenMasked ?: "****") + ")"

                                // Update visual kartu debit di tab Pembayaran
                                binding.tvCardBankDisplay.text = dash.debit.bankName
                                binding.tvCardNumberDisplay.text = dash.debit.cardTokenMasked ?: "4811 11** **** 1234"
                                binding.tvCardStatusDisplay.text = "Aktif Terhubung"
                                binding.tvCardStatusDisplay.setTextColor(0xFFA7F3D0.toInt())

                                val bIdx = bankList.indexOfFirst { it.contains(dash.debit.bankName, ignoreCase = true) }
                                if (bIdx != -1 && binding.spnMhsBank.selectedItemPosition != bIdx) {
                                    binding.spnMhsBank.setSelection(bIdx)
                                }

                                // Load nomor kartu ke field input agar terisi otomatis sesuai web
                                val savedCard = dash.debit.cardTokenMasked
                                if (!savedCard.isNullOrBlank() && binding.edtMhsCardNumber.text.isNullOrEmpty()) {
                                    binding.edtMhsCardNumber.setText(savedCard)
                                }
                            } else {
                                binding.tvMhsDebitBadge.text = "Belum Terhubung"
                                binding.tvMhsDebitBadge.setBackgroundColor(0xFFFEF3C7.toInt())
                                binding.tvMhsDebitBadge.setTextColor(0xFF92400E.toInt())
                                binding.tvMhsDebitInfo.text = "Tautkan kartu debit untuk aktivasi checkout POS."

                                binding.tvCardStatusDisplay.text = "Belum Tertaut"
                                binding.tvCardStatusDisplay.setTextColor(0xFFFDE68A.toInt())
                            }

                            // Status Biometrik (Dashboard & Tab Khusus)
                            if (dash.biometric != null) {
                                // Dashboard Card
                                binding.tvMhsBioBadge.text = "Aktif"
                                binding.tvMhsBioBadge.setBackgroundColor(0xFFD1FAE5.toInt())
                                binding.tvMhsBioBadge.setTextColor(0xFF065F46.toInt())
                                binding.tvMhsBioInfo.text = "Metode biometrik aktif untuk otorisasi pembayaran instan di kasir."

                                binding.tvMhsBioSlotBadge.visibility = View.GONE

                                val regTime = dash.biometric.registeredAt
                                if (!regTime.isNullOrBlank()) {
                                    val formattedReg = FormatUtil.formatTrxDate(regTime)
                                    binding.tvMhsBioTime.text = "Terdaftar: " + formattedReg
                                    binding.tvMhsBioTime.visibility = View.VISIBLE
                                } else {
                                    binding.tvMhsBioTime.visibility = View.GONE
                                }

                                binding.tvMhsBioDesc.visibility = View.GONE
                                binding.btnMhsRegisterBio.text = "Perbarui Sidik Jari"
                                binding.btnMhsRegisterBio.backgroundTintList = ColorStateList.valueOf(0xFFF3F4F6.toInt())
                                binding.btnMhsRegisterBio.setTextColor(0xFF111827.toInt())
                                binding.btnMhsRegisterBio.strokeColor = ColorStateList.valueOf(0xFFE5E7EB.toInt())
                                binding.btnMhsRegisterBio.strokeWidth = 2

                                // Tab Khusus Sidik Jari
                                binding.tvTabBioBadge.text = "Aktif"
                                binding.tvTabBioBadge.setBackgroundColor(0xFFD1FAE5.toInt())
                                binding.tvTabBioBadge.setTextColor(0xFF065F46.toInt())
                                binding.tvTabBioInfo.text = "Metode biometrik aktif untuk otorisasi pembayaran instan di kasir."
                                binding.tvTabBioSlotBadge.visibility = View.GONE
                                if (!regTime.isNullOrBlank()) {
                                    val formattedReg = FormatUtil.formatTrxDate(regTime)
                                    binding.tvTabBioTime.text = "Terdaftar: " + formattedReg
                                    binding.tvTabBioTime.visibility = View.VISIBLE
                                } else {
                                    binding.tvTabBioTime.visibility = View.GONE
                                }
                                binding.btnTabTriggerBioEnroll.text = "PERBARUI SIDIK JARI"
                                binding.btnTabTriggerBioEnroll.backgroundTintList = ColorStateList.valueOf(0xFFF3F4F6.toInt())
                                binding.btnTabTriggerBioEnroll.setTextColor(0xFF111827.toInt())
                                binding.btnTabTriggerBioEnroll.strokeColor = ColorStateList.valueOf(0xFFE5E7EB.toInt())
                                binding.btnTabTriggerBioEnroll.strokeWidth = 2

                            } else {
                                // Dashboard Card
                                binding.tvMhsBioBadge.text = "Belum Terhubung"
                                binding.tvMhsBioBadge.setBackgroundColor(0xFFFEF3C7.toInt())
                                binding.tvMhsBioBadge.setTextColor(0xFF92400E.toInt())
                                binding.tvMhsBioInfo.text = "Tautkan sidik jari Anda ke scanner kasir untuk pembayaran instan tanpa kartu."

                                binding.tvMhsBioSlotBadge.visibility = View.GONE
                                binding.tvMhsBioTime.visibility = View.GONE
                                binding.tvMhsBioDesc.visibility = View.VISIBLE

                                binding.btnMhsRegisterBio.text = "Daftarkan Sidik Jari"
                                binding.btnMhsRegisterBio.backgroundTintList = ColorStateList.valueOf(0xFF111827.toInt())
                                binding.btnMhsRegisterBio.setTextColor(0xFFFFFFFF.toInt())
                                binding.btnMhsRegisterBio.strokeWidth = 0

                                // Tab Khusus Sidik Jari
                                binding.tvTabBioBadge.text = "Belum Terhubung"
                                binding.tvTabBioBadge.setBackgroundColor(0xFFFEF3C7.toInt())
                                binding.tvTabBioBadge.setTextColor(0xFF92400E.toInt())
                                binding.tvTabBioInfo.text = "Tautkan sidik jari Anda ke sensor scanner kasir untuk pembayaran instan tanpa kartu."
                                binding.tvTabBioSlotBadge.visibility = View.GONE
                                binding.tvTabBioTime.visibility = View.GONE
                                binding.btnTabTriggerBioEnroll.text = "DAFTARKAN SIDIK JARI"
                                binding.btnTabTriggerBioEnroll.backgroundTintList = ColorStateList.valueOf(0xFF111827.toInt())
                                binding.btnTabTriggerBioEnroll.setTextColor(0xFFFFFFFF.toInt())
                                binding.btnTabTriggerBioEnroll.strokeWidth = 0
                            }

                            // Transaksi Belanja Terakhir (Dashboard)
                            if (dash.recentTransactions.isNotEmpty()) {
                                recentTrxAdapter.updateData(dash.recentTransactions)
                                binding.tvMhsRecentEmpty.visibility = View.GONE
                                binding.rvMhsRecentTrx.visibility = View.VISIBLE
                            } else {
                                recentTrxAdapter.updateData(emptyList())
                                binding.tvMhsRecentEmpty.visibility = View.VISIBLE
                                binding.rvMhsRecentTrx.visibility = View.GONE
                            }
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
        apiClient.get("/mahasiswa/riwayat?user_id=" + userId) { success, response, _ ->
            runOnUiThread {
                if (success && response != null) {
                    try {
                        val json = JSONObject(response)
                        val arrayStr = json.getJSONArray("data").toString()
                        val type = object : TypeToken<List<Transaction>>() {}.type
                        fullRiwayatList = apiClient.gson.fromJson(arrayStr, type)
                        allRiwayatAdapter.updateData(fullRiwayatList)
                        binding.tvMhsAllRiwayatEmpty.visibility = if (fullRiwayatList.isEmpty()) View.VISIBLE else View.GONE
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
        realTimeHandler.removeCallbacks(realTimeRunnable)
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
