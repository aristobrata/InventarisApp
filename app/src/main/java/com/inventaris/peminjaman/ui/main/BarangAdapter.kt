package com.inventaris.peminjaman.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.inventaris.peminjaman.R
import com.inventaris.peminjaman.data.entity.BarangEntity
import com.inventaris.peminjaman.data.entity.TipeBarang
import com.inventaris.peminjaman.databinding.ItemBarangBinding

class BarangAdapter(
    private val onPinjamClick: (BarangEntity) -> Unit,
    private val onAmbilClick: (BarangEntity) -> Unit,
    private val onTambahStokClick: (BarangEntity) -> Unit,
    private val onEditClick: (BarangEntity) -> Unit,
    private val onHapusClick: (BarangEntity) -> Unit
) : ListAdapter<BarangEntity, BarangAdapter.BarangViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BarangViewHolder {
        val binding = ItemBarangBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BarangViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BarangViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BarangViewHolder(private val binding: ItemBarangBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(barang: BarangEntity) {
            val ctx = binding.root.context
            val isHabisPakai = barang.tipeBarang == TipeBarang.HABIS_PAKAI
            val isPinjam = barang.tipeBarang == TipeBarang.PINJAM
            val isKeduanya = barang.tipeBarang == TipeBarang.KEDUANYA

            binding.tvNamaBarang.text = barang.namaBarang
            binding.tvKodeBarang.text = "${barang.kodeBarang} • ${barang.kategori}"

            // Badge tipe barang
            val warnaTipe = when {
                isHabisPakai -> ContextCompat.getColor(ctx, R.color.tipe_habis_pakai)
                isPinjam -> ContextCompat.getColor(ctx, R.color.tipe_pinjam)
                else -> ContextCompat.getColor(ctx, R.color.primary)
            }
            val warnaTipeBg = when {
                isHabisPakai -> ContextCompat.getColor(ctx, R.color.tipe_habis_pakai_bg)
                isPinjam -> ContextCompat.getColor(ctx, R.color.tipe_pinjam_bg)
                else -> ContextCompat.getColor(ctx, R.color.neutral_bg)
            }
            binding.tvBadgeTipe.text = when {
                isHabisPakai -> "Habis Pakai"
                isPinjam -> "Dipinjamkan"
                else -> "Pinjam & Pakai"
            }
            binding.tvBadgeTipe.setTextColor(warnaTipe)
            binding.tvBadgeTipe.background.setTint(warnaTipeBg)
            binding.viewAksenTipe.setBackgroundColor(warnaTipe)

            // Bar rasio stok tersedia / total
            val rasio = if (barang.totalStok > 0) {
                barang.stokTersedia.toFloat() / barang.totalStok.toFloat()
            } else 0f
            binding.viewStockBarFill.post {
                val lebarTrack = (binding.viewStockBarFill.parent as? android.view.View)?.width ?: 0
                val params = binding.viewStockBarFill.layoutParams
                params.width = (lebarTrack * rasio).toInt().coerceAtLeast(0)
                binding.viewStockBarFill.layoutParams = params
            }
            val warnaBar = when {
                barang.stokTersedia == 0 -> R.color.danger
                rasio < 0.3f -> R.color.warning
                else -> R.color.success
            }
            binding.viewStockBarFill.background.setTint(ContextCompat.getColor(ctx, warnaBar))

            // Detail stok
            binding.tvStokDetail.text = when {
                isHabisPakai -> "Tersedia ${barang.stokTersedia} • Terpakai ${barang.stokTerpakai} • dari total ${barang.totalStok} ${barang.satuan}"
                isPinjam -> "Tersedia ${barang.stokTersedia} • Dipinjam ${barang.stokDipinjam} • dari total ${barang.totalStok} ${barang.satuan}"
                else -> "Tersedia ${barang.stokTersedia} • Dipinjam ${barang.stokDipinjam} • Terpakai ${barang.stokTerpakai} • total ${barang.totalStok} ${barang.satuan}"
            }

            // Aksi Tombol
            val bisaTransaksi = barang.stokTersedia > 0
            
            if (isKeduanya) {
                binding.btnAksiUtama.text = "Pinjam"
                binding.btnAksiUtama.visibility = android.view.View.VISIBLE
                binding.btnAksiUtama.isEnabled = bisaTransaksi
                binding.btnAksiUtama.setOnClickListener { onPinjamClick(barang) }

                binding.btnAksiKedua.text = "Ambil"
                binding.btnAksiKedua.visibility = android.view.View.VISIBLE
                binding.btnAksiKedua.isEnabled = bisaTransaksi
                binding.btnAksiKedua.setOnClickListener { onAmbilClick(barang) }
            } else {
                binding.btnAksiKedua.visibility = android.view.View.GONE
                binding.btnAksiUtama.visibility = android.view.View.VISIBLE
                binding.btnAksiUtama.isEnabled = bisaTransaksi
                
                if (isHabisPakai) {
                    binding.btnAksiUtama.text = "Ambil"
                    binding.btnAksiUtama.setOnClickListener { onAmbilClick(barang) }
                } else {
                    binding.btnAksiUtama.text = "Pinjam"
                    binding.btnAksiUtama.setOnClickListener { onPinjamClick(barang) }
                }
            }

            binding.btnTambahStok.setOnClickListener { onTambahStokClick(barang) }

            binding.root.setOnClickListener { onEditClick(barang) }
            binding.root.setOnLongClickListener {
                onHapusClick(barang)
                true
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<BarangEntity>() {
            override fun areItemsTheSame(oldItem: BarangEntity, newItem: BarangEntity) =
                oldItem.idBarang == newItem.idBarang

            override fun areContentsTheSame(oldItem: BarangEntity, newItem: BarangEntity) =
                oldItem == newItem
        }
    }
}
