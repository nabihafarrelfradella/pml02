package com.example.pml_uts.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object FormatUtil {

    fun resolveImageUrl(raw: String?, defaultBaseUrl: String = "https://myfin.web.id"): String? {
        if (raw.isNullOrBlank()) return null
        val clean = raw.trim()
        if (clean.startsWith("http://") || clean.startsWith("https://")) {
            return clean
        }
        val cleanBase = defaultBaseUrl.replace("/api/mobile", "").trimEnd('/')
        val trimmedPath = clean.trimStart('/')
        return if (trimmedPath.startsWith("storage/")) {
            "$cleanBase/$trimmedPath"
        } else {
            "$cleanBase/storage/$trimmedPath"
        }
    }

    fun formatTrxDate(raw: String?): String {
        if (raw.isNullOrBlank()) return "-"
        return try {
            val parsers = listOf(
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                },
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                },
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                },
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
                SimpleDateFormat("yyyy-MM-dd", Locale.US)
            )

            var parsedDate: Date? = null
            for (parser in parsers) {
                try {
                    parsedDate = parser.parse(raw)
                    if (parsedDate != null) break
                } catch (_: Exception) {}
            }

            if (parsedDate != null) {
                val outputFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Jakarta")
                }
                outputFormat.format(parsedDate) + " WIB"
            } else {
                raw.replace("T", " ").substringBefore(".")
            }
        } catch (_: Exception) {
            raw.replace("T", " ").substringBefore(".")
        }
    }

    fun formatDisplayDateOnly(raw: String?): String {
        if (raw.isNullOrBlank()) return "-"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val parsedDate = parser.parse(raw.take(10))
            if (parsedDate != null) {
                val out = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
                out.format(parsedDate)
            } else raw.take(10)
        } catch (_: Exception) {
            raw.take(10)
        }
    }
}
