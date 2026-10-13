package com.example.pml_uts.ui.kantin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.pml_uts.R
import com.example.pml_uts.data.model.Transaction
import com.example.pml_uts.util.FormatUtil
import java.text.NumberFormat
import java.util.Locale

class KantinTransactionAdapter(
    private var list: List<Transaction>,
    private val onItemClick: ((Transaction) -> Unit)? = null,
    private val onItemLongClick: ((Transaction) -> Unit)? = null
) : RecyclerView.Adapter<KantinTransactionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTrxCode: TextView = view.findViewById(R.id.tvTrxCode)
        val tvTrxStatus: TextView = view.findViewById(R.id.tvTrxStatus)
        val tvTrxParty: TextView = view.findViewById(R.id.tvTrxParty)
        val tvTrxTotal: TextView = view.findViewById(R.id.tvTrxTotal)
        val tvTrxDate: TextView = view.findViewById(R.id.tvTrxDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.tvTrxCode.text = item.transactionCode

        if (item.student != null) {
            holder.tvTrxParty.text = "Pembeli: ${item.student.name}"
        } else if (item.kantin != null) {
            holder.tvTrxParty.text = "Stand: ${item.kantin.namaKantin}"
        } else {
            holder.tvTrxParty.text = "Transaksi Pembelian Kantin"
        }

        val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
        holder.tvTrxTotal.text = formatRupiah.format(item.totalAmount).replace(",00", "")

        // Format tanggal rapi standar Indonesia (WIB) bukan string ISO mentah
        holder.tvTrxDate.text = FormatUtil.formatTrxDate(item.createdAt)

        when (item.status) {
            "success" -> {
                holder.tvTrxStatus.text = "Berhasil"
                holder.tvTrxStatus.setBackgroundColor(0xFFD1FAE5.toInt())
                holder.tvTrxStatus.setTextColor(0xFF065F46.toInt())
            }
            "pending" -> {
                holder.tvTrxStatus.text = "Menunggu"
                holder.tvTrxStatus.setBackgroundColor(0xFFFEF3C7.toInt())
                holder.tvTrxStatus.setTextColor(0xFF92400E.toInt())
            }
            else -> {
                holder.tvTrxStatus.text = "Gagal"
                holder.tvTrxStatus.setBackgroundColor(0xFFFEE2E2.toInt())
                holder.tvTrxStatus.setTextColor(0xFF991B1B.toInt())
            }
        }

        holder.itemView.setOnClickListener {
            onItemClick?.invoke(item)
        }

        holder.itemView.setOnLongClickListener {
            onItemLongClick?.invoke(item)
            true
        }
    }

    override fun getItemCount(): Int = list.size

    fun updateData(newList: List<Transaction>) {
        list = newList
        notifyDataSetChanged()
    }
}
