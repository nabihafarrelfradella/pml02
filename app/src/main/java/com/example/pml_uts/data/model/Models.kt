package com.example.pml_uts.data.model

import com.google.gson.annotations.SerializedName

data class User(
    val id: Int,
    val name: String,
    val email: String,
    val role: String,
    val photo: String? = null
)

data class Kantin(
    val id: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("nama_kantin") val namaKantin: String,
    @SerializedName("no_stand") val noStand: String,
    @SerializedName("status_verifikasi") var statusVerifikasi: String,
    val user: User? = null,
    val rekening: RekeningKantin? = null
)

data class RekeningKantin(
    val id: Int? = null,
    @SerializedName("kantin_id") val kantinId: Int? = null,
    @SerializedName("bank_name") val bankName: String,
    @SerializedName("account_number") val accountNumber: String,
    @SerializedName("account_holder_name") val accountHolderName: String
)

data class DebitAccount(
    val id: Int? = null,
    @SerializedName("user_id") val userId: Int? = null,
    @SerializedName("bank_name") val bankName: String,
    @SerializedName("card_token_masked") val cardTokenMasked: String? = null,
    @SerializedName("is_active") val isActive: Boolean = true
)

data class Biometric(
    val id: Int? = null,
    @SerializedName("user_id") val userId: Int? = null,
    @SerializedName("fingerprint_id") val fingerprintId: Int,
    @SerializedName("fingerprint_hash") val fingerprintHash: String? = null,
    @SerializedName("registered_at") val registeredAt: String? = null
)

data class Menu(
    val id: Int,
    @SerializedName("kantin_id") val kantinId: Int,
    @SerializedName("nama_menu") var namaMenu: String,
    var kategori: String? = "makanan",
    var harga: Double,
    var stock: Int,
    @SerializedName("foto_menu") var fotoMenu: String? = null,
    @SerializedName("foto_url") var fotoUrl: String? = null
)

data class Transaction(
    val id: Int,
    @SerializedName("transaction_code") val transactionCode: String,
    @SerializedName("total_amount") val totalAmount: Double,
    val status: String,
    @SerializedName("created_at") val createdAt: String? = null,
    val kantin: Kantin? = null,
    val student: User? = null
)

data class LoginResponse(
    val success: Boolean,
    val message: String,
    val user: User? = null,
    val extra: Map<String, Any>? = null
)

data class KantinDashboardResponse(
    val success: Boolean,
    val kantin: Kantin? = null,
    val summary: KantinSummary? = null,
    @SerializedName("recent_transactions") val recentTransactions: List<Transaction> = emptyList()
)

data class KantinSummary(
    @SerializedName("total_revenue") val totalRevenue: Long = 0,
    @SerializedName("total_transactions") val totalTransactions: Int = 0,
    @SerializedName("status_verifikasi") val statusVerifikasi: String = "pending"
)

data class MahasiswaDashboardResponse(
    val success: Boolean,
    val user: User? = null,
    val debit: DebitAccount? = null,
    val biometric: Biometric? = null,
    @SerializedName("recent_transactions") val recentTransactions: List<Transaction> = emptyList()
)