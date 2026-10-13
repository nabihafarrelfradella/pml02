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
import com.example.pml_uts.data.model.Kantin
import com.example.pml_uts.util.FormatUtil

class AdminKantinAdapter(
    private var list: List<Kantin>,
    private val onApprove: (Kantin) -> Unit
) : RecyclerView.Adapter<AdminKantinAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgKantin: ImageView = view.findViewById(R.id.imgKantin)
        val tvNamaKantin: TextView = view.findViewById(R.id.tvNamaKantin)
        val tvPemilik: TextView = view.findViewById(R.id.tvPemilik)
        val tvNoStand: TextView = view.findViewById(R.id.tvNoStand)
        val tvStatusBadge: TextView = view.findViewById(R.id.tvStatusBadge)
        val tvRekening: TextView = view.findViewById(R.id.tvRekeningKantin)
        val btnApprove: Button = view.findViewById(R.id.btnApprove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_kantin, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.tvNamaKantin.text = item.namaKantin
        holder.tvPemilik.text = "Pemilik: ${item.user?.name ?: "-"}"
        holder.tvNoStand.text = "Lokasi: ${item.noStand}"

        // Sinkronisasi Foto Pemilik Stand dari Web
        val photoUrl = FormatUtil.resolveImageUrl(item.user?.photoUrl ?: item.user?.photo)
        if (!photoUrl.isNullOrEmpty()) {
            holder.imgKantin.load(photoUrl) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_agenda)
                error(android.R.drawable.ic_menu_agenda)
                transformations(CircleCropTransformation())
            }
        } else {
            holder.imgKantin.setImageResource(android.R.drawable.ic_menu_agenda)
        }

        if (item.statusVerifikasi == "approved") {
            holder.tvStatusBadge.text = "Aktif"
            holder.tvStatusBadge.setBackgroundColor(0xFFD1FAE5.toInt())
            holder.tvStatusBadge.setTextColor(0xFF065F46.toInt())
            holder.btnApprove.visibility = View.GONE
        } else {
            holder.tvStatusBadge.text = "Pending"
            holder.tvStatusBadge.setBackgroundColor(0xFFFEF3C7.toInt())
            holder.tvStatusBadge.setTextColor(0xFF92400E.toInt())
            holder.btnApprove.visibility = View.VISIBLE
            holder.btnApprove.setOnClickListener { onApprove(item) }
        }

        // Tampilkan info rekening pencairan yang sinkron dari web
        if (item.rekening != null) {
            holder.tvRekening.text = "Rek: ${item.rekening.bankName} - ${item.rekening.accountNumber}"
        } else {
            holder.tvRekening.text = "Rek: Belum diatur"
        }
    }

    override fun getItemCount(): Int = list.size

    fun updateData(newList: List<Kantin>) {
        list = newList
        notifyDataSetChanged()
    }
}
