package com.inventaris.peminjaman.ui.stok

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.inventaris.peminjaman.InventarisApp
import com.inventaris.peminjaman.data.entity.BarangEntity
import com.inventaris.peminjaman.databinding.ActivityTambahStokBinding
import com.inventaris.peminjaman.repository.HasilOperasi
import kotlinx.coroutines.launch

/**
 * Layar fitur PENAMBAHAN STOK. Bisa dibuka dengan barang sudah terpilih
 * (dari tombol "+ Stok" di kartu barang) atau kosong (dari menu toolbar),
 * di mana pengguna memilih sendiri barangnya dari dropdown.
 */
class TambahStokActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTambahStokBinding
    private val repository by lazy { (application as InventarisApp).repository }

    private var daftarBarang: List<BarangEntity> = emptyList()
    private var barangTerpilih: BarangEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTambahStokBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val idBarangAwal = intent.getLongExtra(EXTRA_ID_BARANG, -1L)

        lifecycleScope.launch {
            daftarBarang = repository.getAllBarangOnce()
            if (daftarBarang.isEmpty()) {
                Toast.makeText(this@TambahStokActivity, "Belum ada barang. Tambah barang dulu.", Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }

            val namaTampil = daftarBarang.map { "${it.namaBarang} (${it.kodeBarang}) — Stok: ${it.totalStok} ${it.satuan}" }
            val adapterSpinner = ArrayAdapter(this@TambahStokActivity, android.R.layout.simple_spinner_dropdown_item, namaTampil)
            binding.spinnerBarang.setAdapter(adapterSpinner)

            val indexAwal = daftarBarang.indexOfFirst { it.idBarang == idBarangAwal }
            if (indexAwal != -1) {
                binding.spinnerBarang.setText(namaTampil[indexAwal], false)
                pilihBarang(daftarBarang[indexAwal])
            }

            binding.spinnerBarang.setOnItemClickListener { _, _, position, _ ->
                pilihBarang(daftarBarang[position])
            }
        }

        binding.btnSimpanStok.setOnClickListener { simpanTambahStok() }
    }

    private fun pilihBarang(barang: BarangEntity) {
        barangTerpilih = barang
        binding.tvInfoStokSaatIni.visibility = android.view.View.VISIBLE
        binding.tvInfoStokSaatIni.text =
            "Stok saat ini: ${barang.totalStok} ${barang.satuan} " +
                    "(Tersedia: ${barang.stokTersedia}, Dipinjam/Terpakai: ${barang.stokDipinjam + barang.stokTerpakai})"
    }

    private fun simpanTambahStok() {
        val barang = barangTerpilih
        if (barang == null) {
            Toast.makeText(this, "Pilih barang terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        val jumlahStr = binding.etJumlahTambah.text.toString().trim()
        val jumlah = jumlahStr.toIntOrNull()
        if (jumlah == null || jumlah <= 0) {
            Toast.makeText(this, "Jumlah tambah stok tidak valid", Toast.LENGTH_SHORT).show()
            return
        }
        val keterangan = binding.etKeterangan.text.toString().trim().ifEmpty { null }

        binding.btnSimpanStok.isEnabled = false
        lifecycleScope.launch {
            val hasil = repository.tambahStokBarang(
                idBarang = barang.idBarang,
                jumlahTambah = jumlah,
                tanggalMillis = System.currentTimeMillis(),
                keterangan = keterangan
            )
            binding.btnSimpanStok.isEnabled = true

            when (hasil) {
                is HasilOperasi.Sukses -> {
                    Toast.makeText(this@TambahStokActivity, hasil.pesan, Toast.LENGTH_LONG).show()
                    finish()
                }
                is HasilOperasi.Gagal -> {
                    Toast.makeText(this@TambahStokActivity, hasil.pesan, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        const val EXTRA_ID_BARANG = "extra_id_barang"
    }
}
