package com.example.pml_uts.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.pml_uts.data.local.SessionManager
import com.example.pml_uts.data.model.LoginResponse
import com.example.pml_uts.data.remote.ApiClient
import com.example.pml_uts.databinding.ActivityLoginBinding
import com.example.pml_uts.ui.admin.AdminMainActivity
import com.example.pml_uts.ui.kantin.KantinMainActivity
import com.example.pml_uts.ui.mahasiswa.MahasiswaMainActivity
import com.example.pml_uts.util.NotificationSoundUtil
import com.google.android.material.snackbar.Snackbar
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        apiClient = ApiClient(this)

        // Jika sudah login sebelumnya, langsung arahkan ke portal sesuai role
        if (sessionManager.isLoggedIn()) {
            navigateBasedOnRole(sessionManager.getUserRole())
            finish()
            return
        }

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.edtEmail.text.toString().trim()
            val password = binding.edtPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                showSimpleLoginError("Silakan isi email dan kata sandi Anda.")
                return@setOnClickListener
            }

            performLogin(email, password)
        }

        binding.tvToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Otomatis sembunyikan banner error ketika pengguna mulai mengetik ulang
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (binding.cardLoginError.visibility == View.VISIBLE) {
                    binding.cardLoginError.visibility = View.GONE
                }
                binding.edtPassword.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        binding.edtEmail.addTextChangedListener(textWatcher)
        binding.edtPassword.addTextChangedListener(textWatcher)
    }

    private fun performLogin(email: String, pass: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false
        binding.cardLoginError.visibility = View.GONE
        binding.edtEmail.error = null
        binding.edtPassword.error = null

        val json = JSONObject().apply {
            put("email", email)
            put("password", pass)
        }.toString()

        apiClient.post("/login", json) { success, response, error ->
            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                binding.btnLogin.isEnabled = true

                if (success && response != null) {
                    try {
                        val loginRes = apiClient.gson.fromJson(response, LoginResponse::class.java)
                        if (loginRes.success && loginRes.user != null) {
                            NotificationSoundUtil.playSuccessBeep()
                            sessionManager.saveLogin(
                                id = loginRes.user.id,
                                name = loginRes.user.name,
                                email = loginRes.user.email,
                                role = loginRes.user.role,
                                remember = true
                            )

                            Toast.makeText(this, "Selamat datang, !", Toast.LENGTH_SHORT).show()
                            navigateBasedOnRole(loginRes.user.role)
                            finish()
                        } else {
                            showSimpleLoginError(loginRes.message)
                        }
                    } catch (e: Exception) {
                        showSimpleLoginError("Respon server tidak valid: ")
                    }
                } else {
                    var displayMessage = "Email atau kata sandi salah. Silakan periksa kembali."
                    if (!response.isNullOrEmpty()) {
                        try {
                            val errJson = JSONObject(response)
                            if (errJson.has("message")) {
                                displayMessage = errJson.getString("message")
                            }
                        } catch (_: Exception) {
                            if (error != null && (error.contains("connect", ignoreCase = true) || error.contains("timeout", ignoreCase = true))) {
                                displayMessage = "Tidak dapat terhubung ke server. Periksa koneksi internet."
                            }
                        }
                    } else if (error != null) {
                        if (error.contains("connect", ignoreCase = true) || error.contains("timeout", ignoreCase = true)) {
                            displayMessage = "Tidak dapat terhubung ke server. Periksa koneksi internet."
                        }
                    }

                    showSimpleLoginError(displayMessage)
                }
            }
        }
    }

    private fun showSimpleLoginError(message: String) {
        NotificationSoundUtil.playErrorBeep()

        val displayMsg = if (message.contains("Email atau password salah", ignoreCase = true) ||
            message.contains("Kredensial", ignoreCase = true) ||
            message.contains("kata sandi", ignoreCase = true)) {
            "Email atau kata sandi salah. Silakan periksa kembali."
        } else {
            message
        }

        binding.tvLoginErrorText.text = displayMsg
        binding.cardLoginError.visibility = View.VISIBLE
        binding.edtPassword.error = "Kata sandi salah"
        binding.edtPassword.requestFocus()

        Snackbar.make(binding.root, displayMsg, Snackbar.LENGTH_SHORT).show()
    }

    private fun navigateBasedOnRole(role: String) {
        when (role) {
            "admin" -> startActivity(Intent(this, AdminMainActivity::class.java))
            "kantin" -> startActivity(Intent(this, KantinMainActivity::class.java))
            "mahasiswa" -> startActivity(Intent(this, MahasiswaMainActivity::class.java))
            else -> Toast.makeText(this, "Role tidak dikenali: ", Toast.LENGTH_SHORT).show()
        }
    }
}