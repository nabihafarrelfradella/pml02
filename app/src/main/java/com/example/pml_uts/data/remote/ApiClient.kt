package com.example.pml_uts.data.remote

import android.content.Context
import com.example.pml_uts.data.local.SessionManager
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiClient(private val context: Context) {
    private val sessionManager = SessionManager(context)
    val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    val baseUrl: String
        get() = sessionManager.getBaseUrl()

    private val networkErrorMessage = "Tidak dapat terhubung ke server. Periksa koneksi internet Anda."

    private fun parseErrorMessage(code: Int, body: String?): String {
        if (!body.isNullOrBlank()) {
            try {
                val json = JSONObject(body)
                val msg = when {
                    json.has("message") && !json.isNull("message") -> json.optString("message")
                    json.has("error") && !json.isNull("error") -> json.optString("error")
                    else -> null
                }
                if (!msg.isNullOrBlank()) {
                    return msg
                }
            } catch (_: Exception) {
                // Bukan JSON (misal halaman error HTML)
            }
        }

        return when (code) {
            401, 403 -> "Sesi berakhir atau tidak punya akses"
            404 -> "Data tidak ditemukan"
            422 -> "Data yang dikirim tidak valid"
            in 500..599 -> "Server sedang bermasalah, coba lagi nanti"
            else -> "Terjadi kesalahan (Kode $code)"
        }
    }

    fun get(endpoint: String, callback: (success: Boolean, response: String?, error: String?) -> Unit) {
        val url = if (endpoint.startsWith("http")) endpoint else "$baseUrl$endpoint"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null, networkErrorMessage)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful) {
                    callback(true, body, null)
                } else {
                    callback(false, body, parseErrorMessage(response.code, body))
                }
            }
        })
    }

    fun post(endpoint: String, jsonBody: String, callback: (success: Boolean, response: String?, error: String?) -> Unit) {
        val url = if (endpoint.startsWith("http")) endpoint else "$baseUrl$endpoint"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null, networkErrorMessage)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful) {
                    callback(true, body, null)
                } else {
                    callback(false, body, parseErrorMessage(response.code, body))
                }
            }
        })
    }

    fun delete(endpoint: String, callback: (success: Boolean, response: String?, error: String?) -> Unit) {
        val url = if (endpoint.startsWith("http")) endpoint else "$baseUrl$endpoint"
        val request = Request.Builder()
            .url(url)
            .delete()
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null, networkErrorMessage)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful) {
                    callback(true, body, null)
                } else {
                    callback(false, body, parseErrorMessage(response.code, body))
                }
            }
        })
    }
}