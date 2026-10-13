package com.example.pml_uts.ui

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.pml_uts.R
import com.example.pml_uts.data.remote.ApiClient
import com.example.pml_uts.databinding.ActivityRegisterBinding
import org.json.JSONObject

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var apiClient: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.updatePadding(
                left = insets.left,
                top = insets.top,
                right = insets.right,
                bottom = insets.bottom
            )
            windowInsets
        }

        apiClient = ApiClient(this)

        setupRoleRadioGroup()
        setupListeners()
    }

    private fun setupRoleRadioGroup() {
        binding.rgRole.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbKantin) {
                binding.layoutKantinFields.visibility = View.VISIBLE
            } else {
                binding.layoutKantinFields.visibility = View.GONE
            }
        }
    }

    private fun setupListeners() {
        binding.tvToLogin.setOnClickListener { finish() }

        binding.btnRegister.setOnClickListener {
            val name = binding.edtRegName.text.toString().trim()
            val email = binding.edtRegEmail.text.toString().trim()
            val password = binding.edtRegPassword.text.toString().trim()
            val isKantin = binding.rbKantin.isChecked
            val role = if (isKantin) "kantin" else "mahasiswa"

            if (name.isEmpty()) {
                binding.edtRegName.error = "Nama lengkap wajib diisi"
                binding.edtRegName.requestFocus()
                return@setOnClickListener
            }

            if (email.isEmpty()) {
                binding.edtRegEmail.error = "Email wajib diisi"
                binding.edtRegEmail.requestFocus()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.edtRegEmail.error = "Format email tidak valid"
                binding.edtRegEmail.requestFocus()
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                binding.edtRegPassword.error = "Password wajib diisi"
                binding.edtRegPassword.requestFocus()
                return@setOnClickListener
            }

            if (password.length < 6) {
                binding.edtRegPassword.error = "Password minimal 6 karakter"
                binding.edtRegPassword.requestFocus()
                return@setOnClickListener
            }

            var namaKantin = ""
            var noStand = ""
            if (isKantin) {
                namaKantin = binding.edtNamaKantin.text.toString().trim()
                noStand = binding.edtNoStand.text.toString().trim()

                if (namaKantin.isEmpty()) {
                    binding.edtNamaKantin.error = "Nama stand kantin wajib diisi"
                    binding.edtNamaKantin.requestFocus()
                    return@setOnClickListener
                }

                if (noStand.isEmpty()) {
                    binding.edtNoStand.error = "Nomor/lokasi stand wajib diisi"
                    binding.edtNoStand.requestFocus()
                    return@setOnClickListener
                }
            }

            if (!binding.cbAgree.isChecked) {
                Toast.makeText(this, "Anda harus menyetujui syarat & ketentuan!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val json = JSONObject().apply {
                put("name", name)
                put("email", email)
                put("password", password)
                put("role", role)
                if (isKantin) {
                    put("nama_kantin", namaKantin)
                    put("no_stand", noStand)
                }
            }.toString()

            binding.regProgressBar.visibility = View.VISIBLE
            binding.btnRegister.isEnabled = false

            apiClient.post("/register", json) { success, response, error ->
                runOnUiThread {
                    binding.regProgressBar.visibility = View.GONE
                    binding.btnRegister.isEnabled = true

                    if (success && response != null) {
                        Toast.makeText(this, "Registrasi berhasil! Silakan masuk.", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        Toast.makeText(this, error ?: "Registrasi gagal.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}