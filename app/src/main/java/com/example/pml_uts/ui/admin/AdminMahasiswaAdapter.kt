package com.example.pml_uts.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.pml_uts.R
import com.example.pml_uts.data.model.User

class AdminMahasiswaAdapter(
    private var list: List<User>,
    private val onResetBio: (User) -> Unit
) : RecyclerView.Adapter<AdminMahasiswaAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
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

        // Di response, ekstra data disimpan via model
        // Placeholder check or if biometrics exist
        holder.tvDebitMhs.text = "Role: Mahasiswa"
        holder.tvBioBadge.text = "Status: Terdaftar"

        holder.btnResetBio.setOnClickListener {
            onResetBio(item)
        }
    }

    override fun getItemCount(): Int = list.size

    fun updateData(newList: List<User>) {
        list = newList
        notifyDataSetChanged()
    }
}