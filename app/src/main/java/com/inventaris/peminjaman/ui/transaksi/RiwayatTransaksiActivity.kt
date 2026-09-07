package com.inventaris.peminjaman.ui.transaksi

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.inventaris.peminjaman.InventarisApp
import com.inventaris.peminjaman.data.entity.TransaksiPinjamEntity
import com.inventaris.peminjaman.databinding.ActivityRiwayatTransaksiBinding
import com.inventaris.peminjaman.repository.HasilOperasi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Contoh ALUR LOGIKA PENGEMBALIAN, mencakup ketiga skenario:
 *  - Normal   -> repository.kembalikanBarangNormal(...)
 *  - Hilang   -> repository.kembalikanBarangHilang(...)
 *  - Rusak    -> repository.kembalikanBarangRusak(...)
 * Semua update stok terjadi atomik di dalam repository (db.withTransaction).
 */
class RiwayatTransaksiActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRiwayatTransaksiBinding
    private lateinit var adapter: TransaksiAdapter
    private val repository by lazy { (application as InventarisApp).repository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRiwayatTransaksiBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = TransaksiAdapter(
            onKembalikanNormal = { t -> konfirmasiPengembalian(t, JenisPengembalian.NORMAL) },
            onKembalikanHilang = { t -> konfirmasiPengembalian(t, JenisPengembalian.HILANG) },
            onKembalikanRusak = { t -> konfirmasiPengembalian(t, JenisPengembalian.RUSAK) }
        )
        binding.rvTransaksi.layoutManager = LinearLayoutManager(this)
        binding.rvTransaksi.adapter = adapter

        binding.chipGroupFilterTransaksi.setOnCheckedStateChangeListener { _, _ -> terapkanFilter() }

        lifecycleScope.launch {
            combine(repository.getAllTransaksi(), repository.getAllBarang()) { transaksi, barangList ->
                val petaBarang = barangList.associateBy { it.idBarang }
                transaksi.map { TransaksiTampilan(it, petaBarang[it.idBarang]) }
            }.collect { daftar ->
                semuaTransaksiTampilan = daftar
                terapkanFilter()
            }
        }
    }

    private var semuaTransaksiTampilan: List<TransaksiTampilan> = emptyList()

    private fun terapkanFilter() {
        val hasil = when (binding.chipGroupFilterTransaksi.checkedChipId) {
            binding.chipAktifTransaksi.id -> semuaTransaksiTampilan.filter {
                it.transaksi.status == com.inventaris.peminjaman.data.entity.StatusTransaksi.DIPINJAM
            }
            binding.chipSelesaiTransaksi.id -> semuaTransaksiTampilan.filter {
                it.transaksi.status != com.inventaris.peminjaman.data.entity.StatusTransaksi.DIPINJAM
            }
            else -> semuaTransaksiTampilan
        }
        adapter.submitList(hasil)
        binding.tvKosong.visibility =
            if (hasil.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private enum class JenisPengembalian { NORMAL, HILANG, RUSAK }

    private fun konfirmasiPengembalian(transaksi: TransaksiPinjamEntity, jenis: JenisPengembalian) {
        val inputCatatan = EditText(this).apply {
            hint = "Catatan kondisi barang (opsional)"
        }

        val judul = when (jenis) {
            JenisPengembalian.NORMAL -> "Kembalikan barang (Normal)"
            JenisPengembalian.HILANG -> "Tandai barang HILANG"
            JenisPengembalian.RUSAK -> "Tandai barang RUSAK"
        }

        AlertDialog.Builder(this)
            .setTitle(judul)
            .setMessage("Peminjam: ${transaksi.namaPeminjam}\nJumlah: ${transaksi.jumlahPinjam}")
            .setView(inputCatatan)
            .setPositiveButton("Proses") { _, _ ->
                prosesPengembalian(transaksi, jenis, inputCatatan.text.toString().trim())
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun prosesPengembalian(
        transaksi: TransaksiPinjamEntity,
        jenis: JenisPengembalian,
        catatan: String
    ) {
        lifecycleScope.launch {
            val catatanFinal = catatan.ifEmpty { null }

            // ----> INTI ALUR LOGIKA PENGEMBALIAN <----
            val hasil = when (jenis) {
                JenisPengembalian.NORMAL ->
                    repository.kembalikanBarangNormal(transaksi.idTransaksi, catatanFinal)
                JenisPengembalian.HILANG ->
                    repository.kembalikanBarangHilang(transaksi.idTransaksi, catatanFinal)
                JenisPengembalian.RUSAK ->
                    repository.kembalikanBarangRusak(transaksi.idTransaksi, catatanFinal)
            }

            val pesan = when (hasil) {
                is HasilOperasi.Sukses -> hasil.pesan
                is HasilOperasi.Gagal -> "Gagal: ${hasil.pesan}"
            }
            Toast.makeText(this@RiwayatTransaksiActivity, pesan, Toast.LENGTH_LONG).show()
        }
    }
}
