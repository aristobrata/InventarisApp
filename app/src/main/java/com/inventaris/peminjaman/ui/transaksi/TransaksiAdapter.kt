package com.inventaris.peminjaman.ui.transaksi

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.inventaris.peminjaman.R
import com.inventaris.peminjaman.data.entity.BarangEntity
import com.inventaris.peminjaman.data.entity.JenisTransaksi
import com.inventaris.peminjaman.data.entity.StatusTransaksi
import com.inventaris.peminjaman.data.entity.TransaksiPinjamEntity
import com.inventaris.peminjaman.databinding.ItemTransaksiBinding
import java.text.SimpleDateFormat
import java.util.Locale

data class TransaksiTampilan(
    val transaksi: TransaksiPinjamEntity,
    val barang: BarangEntity?
)

class TransaksiAdapter(
    private val onKembalikanNormal: (TransaksiPinjamEntity) -> Unit,
    private val onKembalikanHilang: (TransaksiPinjamEntity) -> Unit,
    private val onKembalikanRusak: (TransaksiPinjamEntity) -> Unit
) : ListAdapter<TransaksiTampilan, TransaksiAdapter.VH>(DIFF) {

    private val formatTanggal = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("in", "ID"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemTransaksiBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemTransaksiBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TransaksiTampilan) {
            val ctx = binding.root.context
            val t = item.transaksi
            val isPengambilan = t.jenisTransaksi == JenisTransaksi.PENGAMBILAN

            binding.tvNamaBarangTransaksi.text = item.barang?.namaBarang ?: "(barang tidak ditemukan)"

            binding.tvBadgeJenisTransaksi.text = if (isPengambilan) "Pengambilan" else "Peminjaman"
            val warnaJenis = ContextCompat.getColor(
                ctx, if (isPengambilan) R.color.tipe_habis_pakai else R.color.tipe_pinjam
            )
            val warnaJenisBg = ContextCompat.getColor(
                ctx, if (isPengambilan) R.color.tipe_habis_pakai_bg else R.color.tipe_pinjam_bg
            )
            binding.tvBadgeJenisTransaksi.setTextColor(warnaJenis)
            binding.tvBadgeJenisTransaksi.background.setTint(warnaJenisBg)

            val labelPeran = if (isPengambilan) "Pengambil" else "Peminjam"
            val labelTanggal = if (isPengambilan) "Tanggal ambil" else "Tanggal pinjam"

            binding.tvDetailTransaksi.text =
                "$labelPeran: ${t.namaPeminjam}\nJumlah: ${t.jumlahPinjam} ${item.barang?.satuan ?: ""}\n" +
                        "$labelTanggal: ${formatTanggal.format(t.tanggalPinjam)}\nStatus: ${labelStatus(t.status)}"

            val statusAktif = t.status == StatusTransaksi.DIPINJAM
            binding.layoutAksiPengembalian.visibility =
                if (statusAktif) android.view.View.VISIBLE else android.view.View.GONE

            binding.btnKembaliNormal.setOnClickListener { onKembalikanNormal(t) }
            binding.btnKembaliHilang.setOnClickListener { onKembalikanHilang(t) }
            binding.btnKembaliRusak.setOnClickListener { onKembalikanRusak(t) }
        }

        private fun labelStatus(status: String): String = when (status) {
            StatusTransaksi.DIPINJAM -> "Dipinjam (aktif)"
            StatusTransaksi.DIKEMBALIKAN -> "Dikembalikan"
            StatusTransaksi.HILANG -> "Hilang"
            StatusTransaksi.RUSAK -> "Rusak"
            StatusTransaksi.DIAMBIL -> "Diambil (selesai)"
            else -> status
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TransaksiTampilan>() {
            override fun areItemsTheSame(oldItem: TransaksiTampilan, newItem: TransaksiTampilan) =
                oldItem.transaksi.idTransaksi == newItem.transaksi.idTransaksi

            override fun areContentsTheSame(oldItem: TransaksiTampilan, newItem: TransaksiTampilan) =
                oldItem == newItem
        }
    }
}
