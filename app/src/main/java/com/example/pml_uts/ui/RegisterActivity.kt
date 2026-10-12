package com.example.pml_uts.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.pml_uts.R
import com.example.pml_uts.data.remote.ApiClient
import com.example.pml_uts.databinding.ActivityRegisterBinding
import org.json.JSONObject

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var apiClient: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Semua field wajib diisi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
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
                    put("nama_kantin", binding.edtNamaKantin.text.toString().trim().ifEmpty { "Stand $name" })
                    put("no_stand", binding.edtNoStand.text.toString().trim().ifEmpty { "Stand #01" })
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