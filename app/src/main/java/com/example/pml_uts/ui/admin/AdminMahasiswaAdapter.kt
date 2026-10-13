package com.example.pml_uts.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.pml_uts.R
import com.example.pml_uts.data.model.User
import com.example.pml_uts.util.FormatUtil

class AdminMahasiswaAdapter(
    private var list: List<User>,
    private val onResetBio: (User) -> Unit
) : RecyclerView.Adapter<AdminMahasiswaAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgMhs: ImageView = view.findViewById(R.id.imgMhs)
        val tvNamaMhs: TextView = view.findViewById(R.id.tvNamaMhs)
        val tvEmailMhs: TextView = view.findViewById(R.id.tvEmailMhs)
        val tvBioBadge: TextView = view.findViewById(R.id.tvBioBadge)
        val tvDebitMhs: TextView = view.findViewById(R.id.tvDebitMhs)
        val btnResetBio: Button = view.findViewById(R.id.btnResetBio)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_mahasiswa, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.tvNamaMhs.text = item.name
        holder.tvEmailMhs.text = item.email

        // Sinkronisasi Foto Profil Mahasiswa dari Web
        val photoUrl = FormatUtil.resolveImageUrl(item.photoUrl ?: item.photo)
        if (!photoUrl.isNullOrEmpty()) {
            holder.imgMhs.load(photoUrl) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_myplaces)
                error(android.R.drawable.ic_menu_myplaces)
                transformations(CircleCropTransformation())
            }
        } else {
            holder.imgMhs.setImageResource(android.R.drawable.ic_menu_myplaces)
        }

        // Tampilkan info rekening debit (Role mahasiswa sudah tidak ditampilkan karena judul halaman sudah Data Mahasiswa)
        if (item.debitAccount != null) {
            val bank = item.debitAccount.bankName
            val cardMasked = item.debitAccount.cardTokenMasked ?: "Aktif"
            holder.tvDebitMhs.text = "Rek: $bank ($cardMasked)"
        } else {
            holder.tvDebitMhs.text = "Rek: Belum ditautkan"
        }

        // Status Biometrik & Tombol Reset Bio
        if (item.biometric != null) {
            holder.tvBioBadge.text = "Status: Terdaftar"
            holder.tvBioBadge.setBackgroundColor(0xFFD1FAE5.toInt())
            holder.tvBioBadge.setTextColor(0xFF065F46.toInt())
            holder.btnResetBio.visibility = View.VISIBLE
            holder.btnResetBio.setOnClickListener {
                onResetBio(item)
            }
        } else {
            holder.tvBioBadge.text = "Status: Belum Terdaftar"
            holder.tvBioBadge.setBackgroundColor(0xFFFEF3C7.toInt())
            holder.tvBioBadge.setTextColor(0xFF92400E.toInt())
            holder.btnResetBio.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = list.size

    fun updateData(newList: List<User>) {
        list = newList
        notifyDataSetChanged()
    }
}
