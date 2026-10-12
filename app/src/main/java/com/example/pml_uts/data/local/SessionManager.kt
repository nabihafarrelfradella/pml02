package com.example.pml_uts.data.local

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "myfin_session"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_REMEMBER_ME = "remember_me"
        private const val KEY_SAVED_EMAIL = "saved_email"
        private const val KEY_BASE_URL = "base_url"

        // Default localhost emulator, fallback ke IP LAN jika di device fisik
        const val DEFAULT_BASE_URL = "https://myfin.web.id/api/mobile"
    }

    fun saveLogin(id: Int, name: String, email: String, role: String, remember: Boolean = false) {
        val editor = prefs.edit()
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.putInt(KEY_USER_ID, id)
        editor.putString(KEY_USER_NAME, name)
        editor.putString(KEY_USER_EMAIL, email)
        editor.putString(KEY_USER_ROLE, role)
        editor.putBoolean(KEY_REMEMBER_ME, remember)
        if (remember) {
            editor.putString(KEY_SAVED_EMAIL, email)
        } else {
            editor.remove(KEY_SAVED_EMAIL)
        }
        editor.apply()
    }

    fun logout() {
        val remember = prefs.getBoolean(KEY_REMEMBER_ME, false)
        val savedEmail = prefs.getString(KEY_SAVED_EMAIL, "")
        val baseUrl = getBaseUrl()

        val editor = prefs.edit()
        editor.clear()
        if (remember) {
            editor.putBoolean(KEY_REMEMBER_ME, true)
            editor.putString(KEY_SAVED_EMAIL, savedEmail)
        }
        editor.putString(KEY_BASE_URL, baseUrl)
        editor.apply()
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    fun getUserId(): Int = prefs.getInt(KEY_USER_ID, -1)
    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "") ?: ""
    fun getUserEmail(): String = prefs.getString(KEY_USER_EMAIL, "") ?: ""
    fun getUserRole(): String = prefs.getString(KEY_USER_ROLE, "") ?: ""
    fun isRememberMe(): Boolean = prefs.getBoolean(KEY_REMEMBER_ME, false)
    fun getSavedEmail(): String = prefs.getString(KEY_SAVED_EMAIL, "") ?: ""

    fun setBaseUrl(url: String) {
        prefs.edit().putString(KEY_BASE_URL, url).apply()
    }

    fun getBaseUrl(): String {
        val saved = prefs.getString(KEY_BASE_URL, null)
        if (saved.isNullOrEmpty() || saved.contains("10.0.2.2")) {
            return DEFAULT_BASE_URL
        }
        return saved
    }
}