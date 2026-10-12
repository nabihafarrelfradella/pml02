package com.example.pml_uts.util

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SupabaseStorageHelper {
    private const val SUPABASE_URL = "https://fsocbyvbzvvjdbtuhcfm.supabase.co"
    private const val SUPABASE_KEY = "sb_publishable_l8whAkx9zuD2jDMoY70GnQ_ddaY9XGv"
    private const val BUCKET_NAME = "Mobile Lanjut"

    private val supabase = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(Storage)
    }

    fun uploadMenuPhoto(
        fileName: String,
        fileBytes: ByteArray,
        onComplete: (success: Boolean, publicUrl: String?, error: String?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                supabase.storage[BUCKET_NAME].upload(
                    path = fileName,
                    data = fileBytes,
                    upsert = true
                )
                val publicUrl = supabase.storage[BUCKET_NAME].publicUrl(fileName)
                withContext(Dispatchers.Main) {
                    onComplete(true, publicUrl, null)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete(false, null, e.message)
                }
            }
        }
    }
}