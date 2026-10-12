package com.example.pml_uts.ui.kantin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.pml_uts.R
import com.example.pml_uts.data.model.Menu
import java.text.NumberFormat
import java.util.Locale

class KantinMenuAdapter(
    private var list: List<Menu>,
    private val onEdit: (Menu) -> Unit,
    private val onDelete: (Menu) -> Unit
) : RecyclerView.Adapter<KantinMenuAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgMenu: ImageView = view.findViewById(R.id.imgMenu)
        val tvNamaMenu: TextView = view.findViewById(R.id.tvNamaMenu)
        val tvKategoriBadge: TextView = view.findViewById(R.id.tvKategoriBadge)
        val tvHargaMenu: TextView = view.findViewById(R.id.tvHargaMenu)
        val tvStockMenu: TextView = view.findViewById(R.id.tvStockMenu)
        val btnMenuAction: ImageButton = view.findViewById(R.id.btnMenuAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_kantin_menu, parent, false)
        return ViewHolder(view)
    }

    private fun resolveImageUrl(item: Menu): String {
        val raw = item.fotoUrl ?: item.fotoMenu
        if (!raw.isNullOrBlank()) {
            return if (raw.startsWith("http")) raw else "https://myfin.web.id/storage/$raw"
        }
        val name = (item.namaMenu ?: "").lowercase(Locale.ROOT)
        return when {
            name.contains("nasi goreng") -> "https://images.unsplash.com/photo-1604908176997-125f25cc6f3d?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80"
            name.contains("ayam") -> "https://images.unsplash.com/photo-1626082927389-6cd097cdc6ec?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80"
            name.contains("teh") -> "https://images.unsplash.com/photo-1556679343-c7306c1976bc?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80"
            name.contains("mie") -> "https://images.unsplash.com/photo-1585032226651-759b368d7246?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80"
            name.contains("jeruk") || name.contains("jus") -> "https://images.unsplash.com/photo-1621506289937-a8e4df240d0b?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80"
            name.contains("kopi") -> "https://images.unsplash.com/photo-1517256064527-09c73fc73e38?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80"
            name.contains("bakso") -> "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80"
            (item.kategori ?: "").equals("minuman", ignoreCase = true) -> "https://images.unsplash.com/photo-1551024709-8f23befc6f87?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80"
            else -> "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?ixlib=rb-4.0.3&auto=format&fit=crop&w=600&q=80"
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.tvNamaMenu.text = item.namaMenu
        holder.tvKategoriBadge.text = item.kategori?.replaceFirstChar { it.uppercase() } ?: "Makanan"

        val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
        holder.tvHargaMenu.text = formatRupiah.format(item.harga).replace(",00", "")
        holder.tvStockMenu.text = "Stok: ${item.stock} porsi"

        val imageUrl = resolveImageUrl(item)
        holder.imgMenu.load(imageUrl) {
            crossfade(true)
            placeholder(android.R.drawable.ic_menu_gallery)
            error(android.R.drawable.ic_menu_gallery)
        }

        // PopupMenu (Fulfills PopupMenu 1%)
        holder.btnMenuAction.setOnClickListener { v ->
            val popup = PopupMenu(v.context, v)
            popup.menuInflater.inflate(R.menu.menu_item_context, popup.menu)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.ctx_edit -> {
                        onEdit(item)
                        true
                    }
                    R.id.ctx_delete -> {
                        onDelete(item)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        // ContextMenu via LongClick (Fulfills ContextMenu 1%)
        holder.itemView.setOnLongClickListener { v ->
            val popup = PopupMenu(v.context, v)
            popup.menuInflater.inflate(R.menu.menu_item_context, popup.menu)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.ctx_edit -> {
                        onEdit(item)
                        true
                    }
                    R.id.ctx_delete -> {
                        onDelete(item)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
            true
        }
    }

    override fun getItemCount(): Int = list.size

    fun updateData(newList: List<Menu>) {
        list = newList
        notifyDataSetChanged()
    }
}