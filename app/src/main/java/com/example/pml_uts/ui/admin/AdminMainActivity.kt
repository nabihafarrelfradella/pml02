package com.example.pml_uts.ui.admin

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pml_uts.R
import com.example.pml_uts.data.local.SessionManager
import com.example.pml_uts.data.model.Kantin
import com.example.pml_uts.data.model.User
import com.example.pml_uts.data.remote.ApiClient
import com.example.pml_uts.databinding.ActivityAdminMainBinding
import com.example.pml_uts.ui.LoginActivity
import com.example.pml_uts.util.NotificationSoundUtil
import com.google.gson.reflect.TypeToken
import org.json.JSONObject

class AdminMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminMainBinding
    private lateinit var apiClient: ApiClient
    private lateinit var sessionManager: SessionManager

    private var currentTab = R.id.nav_approval
    private var allKantins: List<Kantin> = emptyList()
    private var allMahasiswas: List<User> = emptyList()

    private lateinit var kantinAdapter: AdminKantinAdapter
    private lateinit var mahasiswaAdapter: AdminMahasiswaAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarAdmin)
        apiClient = ApiClient(this)
        sessionManager = SessionManager(this)

        setupRecyclerView()
        setupBottomNav()
        setupSearchAutoComplete()

        loadData()
    }

    private fun setupRecyclerView() {
        binding.rvAdmin.layoutManager = LinearLayoutManager(this)

        kantinAdapter = AdminKantinAdapter(emptyList()) { kantin ->
            showApproveDialog(kantin)
        }

        mahasiswaAdapter = AdminMahasiswaAdapter(emptyList()) { mhs ->
            showResetBioDialog(mhs)
        }

        binding.rvAdmin.adapter = kantinAdapter
    }

    private fun setupBottomNav() {
        binding.adminBottomNav.setOnItemSelectedListener { item ->
            currentTab = item.itemId
            when (item.itemId) {
                R.id.nav_approval -> {
                    binding.tvAdminSectionTitle.text = "Daftar Approval Stand Kantin"
                    binding.rvAdmin.adapter = kantinAdapter
                    kantinAdapter.updateData(allKantins)
                }
                R.id.nav_mahasiswa -> {
                    binding.tvAdminSectionTitle.text = "Data Mahasiswa Terdaftar"
                    binding.rvAdmin.adapter = mahasiswaAdapter
                    mahasiswaAdapter.updateData(allMahasiswas)
                }
            }
            true
        }
    }

    private fun setupSearchAutoComplete() {
        // AutoCompleteTextView filter (Fulfills AutoCompleteTextView 1%)
        binding.actvAdminSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim().lowercase()
                filterList(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterList(query: String) {
        if (currentTab == R.id.nav_approval) {
            val filtered = if (query.isEmpty()) allKantins else {
                allKantins.filter {
                    it.namaKantin.lowercase().contains(query) || (it.user?.name?.lowercase()?.contains(query) ?: false)
                }
            }
            kantinAdapter.updateData(filtered)
        } else {
            val filtered = if (query.isEmpty()) allMahasiswas else {
                allMahasiswas.filter {
                    it.name.lowercase().contains(query) || it.email.lowercase().contains(query)
                }
            }
            mahasiswaAdapter.updateData(filtered)
        }
    }

    private fun loadData() {
        binding.adminProgressBar.visibility = View.VISIBLE

        // Load Kantin
        apiClient.get("/admin/kantin") { success, response, error ->
            runOnUiThread {
                if (success && response != null) {
                    try {
                        val json = JSONObject(response)
                        val dataArray = json.getJSONArray("data").toString()
                        val type = object : TypeToken<List<Kantin>>() {}.type
                        allKantins = apiClient.gson.fromJson(dataArray, type)

                        val searchSuggestions = allKantins.map { it.namaKantin }
                        val actvAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, searchSuggestions)
                        binding.actvAdminSearch.setAdapter(actvAdapter)

                        if (currentTab == R.id.nav_approval) {
                            kantinAdapter.updateData(allKantins)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // Load Mahasiswa
        apiClient.get("/admin/mahasiswa") { success, response, error ->
            runOnUiThread {
                binding.adminProgressBar.visibility = View.GONE
                if (success && response != null) {
                    try {
                        val json = JSONObject(response)
                        val dataArray = json.getJSONArray("data").toString()
                        val type = object : TypeToken<List<User>>() {}.type
                        allMahasiswas = apiClient.gson.fromJson(dataArray, type)

                        if (currentTab == R.id.nav_mahasiswa) {
                            mahasiswaAdapter.updateData(allMahasiswas)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun showApproveDialog(kantin: Kantin) {
        AlertDialog.Builder(this)
            .setTitle("Verifikasi Stand")
            .setMessage("Setujui (Approve) pendaftaran stand ${kantin.namaKantin}?")
            .setPositiveButton("Setujui") { _, _ ->
                apiClient.post("/admin/kantin/${kantin.id}/approve", "{}") { success, _, err ->
                    runOnUiThread {
                        if (success) {
                            NotificationSoundUtil.playSuccessBeep()
                            Toast.makeText(this, "Stand berhasil disetujui!", Toast.LENGTH_SHORT).show()
                            loadData()
                        } else {
                            Toast.makeText(this, err ?: "Gagal memverifikasi", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showResetBioDialog(mhs: User) {
        AlertDialog.Builder(this)
            .setTitle("Reset Biometrik")
            .setMessage("Reset data sidik jari sensor AS608 untuk mahasiswa ${mhs.name}?")
            .setPositiveButton("Reset") { _, _ ->
                apiClient.post("/admin/mahasiswa/${mhs.id}/reset-bio", "{}") { success, _, err ->
                    runOnUiThread {
                        if (success) {
                            NotificationSoundUtil.playSuccessBeep()
                            Toast.makeText(this, "Data biometrik mahasiswa berhasil direset!", Toast.LENGTH_SHORT).show()
                            loadData()
                        } else {
                            Toast.makeText(this, err ?: "Gagal mereset biometrik", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // OptionsMenu (Fulfills OptionsMenu 1%)
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_options_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                loadData()
                Toast.makeText(this, "Memperbarui data...", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_about -> {
                AlertDialog.Builder(this)
                    .setTitle("Tentang MyFin")
                    .setMessage("MyFin Mobile v1.0\nAplikasi Manajemen Finansial & Kantin Digital Polinema\nUTS Pemrograman Mobile Lanjut 2026/2027")
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