package com.inventaris.peminjaman.ui.transaksi

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.inventaris.peminjaman.InventarisApp
import com.inventaris.peminjaman.data.entity.BarangEntity
import com.inventaris.peminjaman.databinding.ActivityPeminjamanBinding
import com.inventaris.peminjaman.repository.HasilOperasi
import kotlinx.coroutines.launch

/**
 * Contoh ALUR LOGIKA PEMINJAMAN end-to-end:
 * 1. Ambil data barang terpilih.
 * 2. User mengisi nama peminjam & jumlah.
 * 3. Panggil repository.pinjamBarang(...) -> semua validasi stok +
 *    update stok + insert riwayat transaksi terjadi atomik di dalam
 *    satu database transaction (lihat InventarisRepository).
 * 4. Tampilkan hasil ke user.
 */
class PeminjamanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPeminjamanBinding
    private var barang: BarangEntity? = null

    private val repository by lazy { (application as InventarisApp).repository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPeminjamanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val idBarang = intent.getLongExtra(EXTRA_ID_BARANG, -1L)

        lifecycleScope.launch {
            barang = repository.getAllBarangOnce().find { it.idBarang == idBarang }
            barang?.let { tampilkanInfoBarang(it) } ?: run {
                Toast.makeText(this@PeminjamanActivity, "Barang tidak ditemukan", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        binding.btnProsesPinjam.setOnClickListener { prosesPinjam() }
    }

    private fun tampilkanInfoBarang(b: BarangEntity) {
        binding.tvNamaBarang.text = b.namaBarang
        binding.tvStokTersedia.text = "Stok tersedia saat ini: ${b.stokTersedia} ${b.satuan}"
    }

    private fun prosesPinjam() {
        val b = barang ?: return
        val nama = binding.etNamaPeminjam.text.toString().trim()
        val jumlahStr = binding.etJumlahPinjam.text.toString().trim()

        if (nama.isEmpty()) {
            Toast.makeText(this, "Nama peminjam wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }
        val jumlah = jumlahStr.toIntOrNull()
        if (jumlah == null || jumlah <= 0) {
            Toast.makeText(this, "Jumlah pinjam tidak valid", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnProsesPinjam.isEnabled = false

        lifecycleScope.launch {
            // ----> INTI ALUR LOGIKA PEMINJAMAN <----
            val hasil = repository.pinjamBarang(
                idBarang = b.idBarang,
                namaPeminjam = nama,
                jumlah = jumlah,
                tanggalPinjamMillis = System.currentTimeMillis()
            )

            binding.btnProsesPinjam.isEnabled = true

            when (hasil) {
                is HasilOperasi.Sukses -> {
                    Toast.makeText(this@PeminjamanActivity, hasil.pesan, Toast.LENGTH_LONG).show()
                    finish()
                }
                is HasilOperasi.Gagal -> {
                    Toast.makeText(this@PeminjamanActivity, hasil.pesan, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        const val EXTRA_ID_BARANG = "extra_id_barang"
    }
}
