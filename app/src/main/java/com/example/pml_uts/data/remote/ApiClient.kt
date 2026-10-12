package com.example.pml_uts.data.remote

import android.content.Context
import com.example.pml_uts.data.local.SessionManager
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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

    fun get(endpoint: String, callback: (success: Boolean, response: String?, error: String?) -> Unit) {
        val url = if (endpoint.startsWith("http")) endpoint else "$baseUrl$endpoint"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null, e.message ?: "Koneksi jaringan gagal")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful) {
                    callback(true, body, null)
                } else {
                    callback(false, body, "Error ${response.code}: $body")
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
                callback(false, null, e.message ?: "Koneksi jaringan gagal")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful) {
                    callback(true, body, null)
                } else {
                    callback(false, body, "Error ${response.code}: $body")
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
                callback(false, null, e.message ?: "Koneksi jaringan gagal")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful) {
                    callback(true, body, null)
                } else {
                    callback(false, body, "Error ${response.code}")
                }
            }
        })
    }
}