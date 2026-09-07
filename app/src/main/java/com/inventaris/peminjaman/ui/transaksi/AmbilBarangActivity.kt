package com.inventaris.peminjaman.ui.transaksi

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.inventaris.peminjaman.InventarisApp
import com.inventaris.peminjaman.data.entity.BarangEntity
import com.inventaris.peminjaman.databinding.ActivityAmbilBarangBinding
import com.inventaris.peminjaman.repository.HasilOperasi
import kotlinx.coroutines.launch

/**
 * Alur logika PENGAMBILAN barang tipe HABIS_PAKAI (consumable). Berbeda dari
 * PeminjamanActivity: tidak ada tanggal kembali / proses pengembalian, karena
 * barang tersebut memang dipakai habis begitu diambil.
 */
class AmbilBarangActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAmbilBarangBinding
    private var barang: BarangEntity? = null
    private val repository by lazy { (application as InventarisApp).repository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAmbilBarangBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val idBarang = intent.getLongExtra(EXTRA_ID_BARANG, -1L)

        lifecycleScope.launch {
            barang = repository.getAllBarangOnce().find { it.idBarang == idBarang }
            barang?.let { tampilkanInfoBarang(it) } ?: run {
                Toast.makeText(this@AmbilBarangActivity, "Barang tidak ditemukan", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        binding.btnProsesAmbil.setOnClickListener { prosesAmbil() }
    }

    private fun tampilkanInfoBarang(b: BarangEntity) {
        binding.tvNamaBarang.text = b.namaBarang
        binding.tvStokTersedia.text = "Stok tersedia saat ini: ${b.stokTersedia} ${b.satuan}"
    }

    private fun prosesAmbil() {
        val b = barang ?: return
        val nama = binding.etNamaPengambil.text.toString().trim()
        val jumlahStr = binding.etJumlahAmbil.text.toString().trim()
        val catatan = binding.etCatatanAmbil.text.toString().trim().ifEmpty { null }

        if (nama.isEmpty()) {
            Toast.makeText(this, "Nama pengambil wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }
        val jumlah = jumlahStr.toIntOrNull()
        if (jumlah == null || jumlah <= 0) {
            Toast.makeText(this, "Jumlah pengambilan tidak valid", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnProsesAmbil.isEnabled = false

        lifecycleScope.launch {
            // ----> INTI ALUR LOGIKA PENGAMBILAN (tanpa pengembalian) <----
            val hasil = repository.ambilBarangHabisPakai(
                idBarang = b.idBarang,
                namaPengambil = nama,
                jumlah = jumlah,
                tanggalMillis = System.currentTimeMillis(),
                catatan = catatan
            )

            binding.btnProsesAmbil.isEnabled = true

            when (hasil) {
                is HasilOperasi.Sukses -> {
                    Toast.makeText(this@AmbilBarangActivity, hasil.pesan, Toast.LENGTH_LONG).show()
                    finish()
                }
                is HasilOperasi.Gagal -> {
                    Toast.makeText(this@AmbilBarangActivity, hasil.pesan, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        const val EXTRA_ID_BARANG = "extra_id_barang"
    }
}
