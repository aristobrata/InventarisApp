package com.inventaris.peminjaman.ui.laporan

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.inventaris.peminjaman.InventarisApp
import com.inventaris.peminjaman.data.entity.JenisTransaksi
import com.inventaris.peminjaman.data.entity.StatusTransaksi
import com.inventaris.peminjaman.databinding.ActivityLaporanBinding
import com.inventaris.peminjaman.util.BarisLaporanTransaksi
import com.inventaris.peminjaman.util.ExcelExporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Pusat fitur LAPORAN EXCEL:
 *  1. Laporan Stok Barang -> kondisi stok master saat ini (snapshot).
 *  2. Laporan Transaksi   -> riwayat peminjaman & pengambilan, dengan filter
 *     opsional berdasarkan jenis transaksi & status.
 */
class LaporanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLaporanBinding
    private val repository by lazy { (application as InventarisApp).repository }

    private val opsiJenis = listOf("Semua Jenis", "Peminjaman", "Pengambilan (Habis Pakai)")
    private val opsiStatus = listOf(
        "Semua Status", "Dipinjam (aktif)", "Dikembalikan", "Hilang", "Rusak", "Diambil (Habis Pakai)"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLaporanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.spinnerJenis.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opsiJenis)
        )
        binding.spinnerJenis.setText(opsiJenis[0], false)

        binding.spinnerStatus.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opsiStatus)
        )
        binding.spinnerStatus.setText(opsiStatus[0], false)

        binding.btnEksporStok.setOnClickListener { eksporLaporanStok() }
        binding.btnEksporTransaksi.setOnClickListener { eksporLaporanTransaksi() }
    }

    private fun eksporLaporanStok() {
        lifecycleScope.launch {
            setLoadingStok(true)
            val daftar = repository.getAllBarangOnce()
            if (daftar.isEmpty()) {
                Toast.makeText(this@LaporanActivity, "Belum ada data barang untuk diekspor", Toast.LENGTH_SHORT).show()
                setLoadingStok(false)
                return@launch
            }
            val uri = ExcelExporter.exportBarangKeExcel(this@LaporanActivity, daftar)
            setLoadingStok(false)
            tampilkanHasilEkspor(uri, "Laporan Stok Barang")
        }
    }

    private fun eksporLaporanTransaksi() {
        lifecycleScope.launch {
            setLoadingTransaksi(true)

            val semuaBarang = repository.getAllBarangOnce().associateBy { it.idBarang }

            // Ambil snapshot pertama dari Flow riwayat transaksi.
            var daftarTransaksi = repository.getAllTransaksi().first()

            val jenisPilihan = binding.spinnerJenis.text.toString()
            val statusPilihan = binding.spinnerStatus.text.toString()

            when (jenisPilihan) {
                "Peminjaman" -> daftarTransaksi = daftarTransaksi.filter { it.jenisTransaksi == JenisTransaksi.PEMINJAMAN }
                "Pengambilan (Habis Pakai)" -> daftarTransaksi = daftarTransaksi.filter { it.jenisTransaksi == JenisTransaksi.PENGAMBILAN }
            }

            when (statusPilihan) {
                "Dipinjam (aktif)" -> daftarTransaksi = daftarTransaksi.filter { it.status == StatusTransaksi.DIPINJAM }
                "Dikembalikan" -> daftarTransaksi = daftarTransaksi.filter { it.status == StatusTransaksi.DIKEMBALIKAN }
                "Hilang" -> daftarTransaksi = daftarTransaksi.filter { it.status == StatusTransaksi.HILANG }
                "Rusak" -> daftarTransaksi = daftarTransaksi.filter { it.status == StatusTransaksi.RUSAK }
                "Diambil (Habis Pakai)" -> daftarTransaksi = daftarTransaksi.filter { it.status == StatusTransaksi.DIAMBIL }
            }

            if (daftarTransaksi.isEmpty()) {
                Toast.makeText(this@LaporanActivity, "Tidak ada transaksi yang cocok dengan filter", Toast.LENGTH_SHORT).show()
                setLoadingTransaksi(false)
                return@launch
            }

            val baris = daftarTransaksi.map { t ->
                val b = semuaBarang[t.idBarang]
                BarisLaporanTransaksi(
                    kodeBarang = b?.kodeBarang ?: "-",
                    namaBarang = b?.namaBarang ?: "(barang telah dihapus)",
                    jenisTransaksi = t.jenisTransaksi,
                    namaOrang = t.namaPeminjam,
                    jumlah = t.jumlahPinjam,
                    satuan = b?.satuan ?: "",
                    tanggalTransaksiMillis = t.tanggalPinjam,
                    tanggalKembaliMillis = t.tanggalKembali,
                    status = t.status,
                    catatan = t.catatan
                )
            }

            val uri = ExcelExporter.exportTransaksiKeExcel(this@LaporanActivity, baris)
            setLoadingTransaksi(false)
            tampilkanHasilEkspor(uri, "Laporan Transaksi")
        }
    }

    private fun tampilkanHasilEkspor(uri: android.net.Uri?, namaLaporan: String) {
        if (uri != null) {
            Toast.makeText(this, "$namaLaporan berhasil diekspor ke folder Downloads", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Gagal mengekspor $namaLaporan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setLoadingStok(loading: Boolean) {
        binding.btnEksporStok.isEnabled = !loading
        binding.btnEksporStok.text = if (loading) "Membuat file..." else "Ekspor Laporan Stok"
    }

    private fun setLoadingTransaksi(loading: Boolean) {
        binding.btnEksporTransaksi.isEnabled = !loading
        binding.btnEksporTransaksi.text = if (loading) "Membuat file..." else "Ekspor Laporan Transaksi"
    }
}
