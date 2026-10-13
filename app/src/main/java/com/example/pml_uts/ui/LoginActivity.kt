package com.example.pml_uts.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.pml_uts.R
import com.example.pml_uts.data.local.SessionManager
import com.example.pml_uts.data.model.LoginResponse
import com.example.pml_uts.data.remote.ApiClient
import com.example.pml_uts.databinding.ActivityLoginBinding
import com.example.pml_uts.ui.admin.AdminMainActivity
import com.example.pml_uts.ui.kantin.KantinMainActivity
import com.example.pml_uts.ui.mahasiswa.MahasiswaMainActivity
import com.example.pml_uts.util.NotificationSoundUtil
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
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

        sessionManager = SessionManager(this)
        apiClient = ApiClient(this)

        // Jika sudah login sebelumnya, langsung arahkan ke portal sesuai role
        if (sessionManager.isLoggedIn()) {
            navigateBasedOnRole(sessionManager.getUserRole())
            finish()
            return
        }

        // Terapkan fitur Remember Me: Hanya isi jika user sebelumnya mencentang "Ingat Saya"
        if (sessionManager.isRememberMe() && sessionManager.getSavedEmail().isNotEmpty()) {
            binding.cbRememberMe.isChecked = true
            binding.edtEmail.setText(sessionManager.getSavedEmail())
        }

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.edtEmail.text.toString().trim()
            val password = binding.edtPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Silakan isi email dan password Anda", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            performLogin(email, password)
        }

        binding.tvToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Server URL sudah otomatis tertanam ke domain resmi VPS (https://myfin.web.id)
    }

    private fun performLogin(email: String, pass: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false

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
                            val remember = binding.cbRememberMe.isChecked
                            sessionManager.saveLogin(
                                id = loginRes.user.id,
                                name = loginRes.user.name,
                                email = loginRes.user.email,
                                role = loginRes.user.role,
                                remember = remember
                            )

                            Toast.makeText(this, "Selamat datang, ${loginRes.user.name}!", Toast.LENGTH_SHORT).show()
                            navigateBasedOnRole(loginRes.user.role)
                            finish()
                        } else {
                            Toast.makeText(this, loginRes.message, Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "Gagal memproses respon server: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, error ?: "Kredensial tidak cocok atau server belum aktif.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private fun navigateBasedOnRole(role: String) {
        when (role) {
            "admin" -> startActivity(Intent(this, AdminMainActivity::class.java))
            "kantin" -> startActivity(Intent(this, KantinMainActivity::class.java))
            "mahasiswa" -> startActivity(Intent(this, MahasiswaMainActivity::class.java))
            else -> Toast.makeText(this, "Role tidak dikenali: $role", Toast.LENGTH_SHORT).show()
        }
    }
}