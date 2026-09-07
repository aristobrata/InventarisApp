package com.inventaris.peminjaman.ui.barang

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.inventaris.peminjaman.InventarisApp
import com.inventaris.peminjaman.R
import com.inventaris.peminjaman.data.entity.BarangEntity
import com.inventaris.peminjaman.data.entity.TipeBarang
import com.inventaris.peminjaman.databinding.ActivityTambahEditBarangBinding
import com.inventaris.peminjaman.repository.HasilOperasi
import kotlinx.coroutines.launch

class TambahEditBarangActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTambahEditBarangBinding
    private var barangDiedit: BarangEntity? = null

    private val repository by lazy { (application as InventarisApp).repository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTambahEditBarangBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val idBarang = intent.getLongExtra(EXTRA_ID_BARANG, -1L)
        if (idBarang != -1L) {
            lifecycleScope.launch {
                val barang = repository.getAllBarangOnce().find { it.idBarang == idBarang }
                barang?.let { isiForm(it) }
            }
        }

        binding.btnSimpan.setOnClickListener { simpan() }
    }

    private fun isiForm(barang: BarangEntity) {
        barangDiedit = barang
        supportActionBar?.title = "Edit Barang"
        binding.etKodeBarang.setText(barang.kodeBarang)
        binding.etNamaBarang.setText(barang.namaBarang)
        binding.etKategori.setText(barang.kategori)
        binding.etSatuan.setText(barang.satuan)
        binding.etTotalStok.setText(barang.totalStok.toString())
        // Saat edit, total stok tidak diubah manual (mengikuti hasil transaksi/tambah stok),
        // jadi field ini dikunci agar tidak merusak konsistensi stok.
        binding.etTotalStok.isEnabled = false
        binding.tvKeteranganStok.text =
            "Tersedia: ${barang.stokTersedia}  |  Dipinjam: ${barang.stokDipinjam}  |  " +
                    "Hilang: ${barang.stokHilang}  |  Rusak: ${barang.stokRusak}  |  Terpakai: ${barang.stokTerpakai}"
        binding.tvKeteranganStok.visibility = android.view.View.VISIBLE

        if (barang.tipeBarang == TipeBarang.HABIS_PAKAI || barang.tipeBarang == TipeBarang.KEDUANYA) {
            binding.chipHabisPakai.isChecked = true
        }
        if (barang.tipeBarang == TipeBarang.PINJAM || barang.tipeBarang == TipeBarang.KEDUANYA) {
            binding.chipDipinjamkan.isChecked = true
        }
        // Tipe barang tidak boleh diubah lagi setelah barang punya riwayat transaksi,
        // supaya laporan lama tidak jadi ambigu (mis. barang pernah "dipinjam" lalu
        // tiba-tiba jadi "habis pakai").
        val sudahPunyaTransaksi = barang.stokDipinjam > 0 || barang.stokHilang > 0 ||
                barang.stokRusak > 0 || barang.stokTerpakai > 0
        binding.chipGroupTipeBarang.isEnabled = !sudahPunyaTransaksi
        binding.chipDipinjamkan.isEnabled = !sudahPunyaTransaksi
        binding.chipHabisPakai.isEnabled = !sudahPunyaTransaksi
        binding.tvKeteranganTipeTerkunci.visibility =
            if (sudahPunyaTransaksi) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun simpan() {
        val kode = binding.etKodeBarang.text.toString().trim()
        val nama = binding.etNamaBarang.text.toString().trim()
        val kategori = binding.etKategori.text.toString().trim()
        val satuan = binding.etSatuan.text.toString().trim()
        val totalStokStr = binding.etTotalStok.text.toString().trim()
        val isPinjam = binding.chipDipinjamkan.isChecked
        val isHabisPakai = binding.chipHabisPakai.isChecked

        val tipeBarang = when {
            isPinjam && isHabisPakai -> TipeBarang.KEDUANYA
            isHabisPakai -> TipeBarang.HABIS_PAKAI
            isPinjam -> TipeBarang.PINJAM
            else -> {
                Toast.makeText(this, "Pilih minimal satu tipe barang", Toast.LENGTH_SHORT).show()
                return
            }
        }

        if (kode.isEmpty() || nama.isEmpty() || satuan.isEmpty() || totalStokStr.isEmpty()) {
            Toast.makeText(this, "Semua field wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }
        val totalStok = totalStokStr.toIntOrNull()
        if (totalStok == null || totalStok < 0) {
            Toast.makeText(this, "Total stok harus berupa angka >= 0", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val hasil = if (barangDiedit == null) {
                repository.tambahBarang(
                    BarangEntity(
                        kodeBarang = kode,
                        namaBarang = nama,
                        kategori = kategori.ifEmpty { "Umum" },
                        totalStok = totalStok,
                        stokTersedia = totalStok,
                        satuan = satuan,
                        tipeBarang = tipeBarang
                    )
                )
            } else {
                repository.updateBarang(
                    barangDiedit!!.copy(
                        kodeBarang = kode,
                        namaBarang = nama,
                        kategori = kategori.ifEmpty { "Umum" },
                        satuan = satuan,
                        tipeBarang = tipeBarang
                        // totalStok & stok lain sengaja TIDAK diubah dari form ini
                    )
                )
            }

            when (hasil) {
                is HasilOperasi.Sukses -> {
                    Toast.makeText(this@TambahEditBarangActivity, hasil.pesan, Toast.LENGTH_SHORT).show()
                    finish()
                }
                is HasilOperasi.Gagal -> {
                    Toast.makeText(this@TambahEditBarangActivity, hasil.pesan, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        const val EXTRA_ID_BARANG = "extra_id_barang"
    }
}
