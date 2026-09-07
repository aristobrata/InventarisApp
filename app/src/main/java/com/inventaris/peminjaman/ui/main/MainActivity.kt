package com.inventaris.peminjaman.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.inventaris.peminjaman.InventarisApp
import com.inventaris.peminjaman.R
import com.inventaris.peminjaman.data.entity.BarangEntity
import com.inventaris.peminjaman.data.entity.TipeBarang
import com.inventaris.peminjaman.databinding.ActivityMainBinding
import com.inventaris.peminjaman.ui.barang.TambahEditBarangActivity
import com.inventaris.peminjaman.ui.laporan.LaporanActivity
import com.inventaris.peminjaman.ui.stok.TambahStokActivity
import com.inventaris.peminjaman.ui.transaksi.AmbilBarangActivity
import com.inventaris.peminjaman.ui.transaksi.PeminjamanActivity
import com.inventaris.peminjaman.ui.transaksi.RiwayatTransaksiActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory((application as InventarisApp).repository)
    }

    private lateinit var adapter: BarangAdapter

    private var filterTipe: String? = null // null = semua
    private var kataKunci: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = BarangAdapter(
            onPinjamClick = { barang -> bukaFormPinjam(barang) },
            onAmbilClick = { barang -> bukaFormAmbil(barang) },
            onTambahStokClick = { barang -> bukaTambahStok(barang) },
            onEditClick = { barang ->
                startActivity(
                    Intent(this, TambahEditBarangActivity::class.java)
                        .putExtra(TambahEditBarangActivity.EXTRA_ID_BARANG, barang.idBarang)
                )
            },
            onHapusClick = { barang -> konfirmasiHapus(barang) }
        )

        binding.rvBarang.layoutManager = LinearLayoutManager(this)
        binding.rvBarang.adapter = adapter

        binding.fabTambahBarang.setOnClickListener {
            startActivity(Intent(this, TambahEditBarangActivity::class.java))
        }

        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            filterTipe = when (checkedIds.firstOrNull()) {
                binding.chipDipinjamkan.id -> TipeBarang.PINJAM
                binding.chipHabisPakai.id -> TipeBarang.HABIS_PAKAI
                else -> null
            }
            terapkanFilter()
        }

        lifecycleScope.launch {
            viewModel.daftarBarang.collect { daftar ->
                daftarBarangSemua = daftar
                terapkanFilter()
                tampilkanStatistik(daftar)
            }
        }

        lifecycleScope.launch {
            viewModel.pesan.collect { pesan ->
                Toast.makeText(this@MainActivity, pesan, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var daftarBarangSemua: List<BarangEntity> = emptyList()

    private fun terapkanFilter() {
        var hasil = daftarBarangSemua
        if (filterTipe != null) {
            hasil = hasil.filter { it.tipeBarang == filterTipe }
        }
        if (kataKunci.isNotBlank()) {
            val kw = kataKunci.trim().lowercase()
            hasil = hasil.filter {
                it.namaBarang.lowercase().contains(kw) ||
                        it.kodeBarang.lowercase().contains(kw) ||
                        it.kategori.lowercase().contains(kw)
            }
        }
        adapter.submitList(hasil)
        binding.tvKosong.visibility =
            if (hasil.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        binding.tvKosong.text = if (daftarBarangSemua.isEmpty()) {
            "Belum ada data barang.\nTekan tombol + untuk menambah."
        } else {
            "Tidak ada barang yang cocok dengan filter/pencarian."
        }
    }

    private fun tampilkanStatistik(daftar: List<BarangEntity>) {
        binding.tvStatJenisBarang.text = daftar.size.toString()
        binding.tvStatDipinjam.text = daftar.sumOf { it.stokDipinjam }.toString()
        binding.tvStatBermasalah.text = daftar.sumOf { it.stokHilang + it.stokRusak }.toString()
    }

    private fun bukaFormPinjam(barang: BarangEntity) {
        startActivity(
            Intent(this, PeminjamanActivity::class.java)
                .putExtra(PeminjamanActivity.EXTRA_ID_BARANG, barang.idBarang)
        )
    }

    private fun bukaFormAmbil(barang: BarangEntity) {
        startActivity(
            Intent(this, AmbilBarangActivity::class.java)
                .putExtra(AmbilBarangActivity.EXTRA_ID_BARANG, barang.idBarang)
        )
    }

    private fun bukaTambahStok(barang: BarangEntity) {
        startActivity(
            Intent(this, TambahStokActivity::class.java)
                .putExtra(TambahStokActivity.EXTRA_ID_BARANG, barang.idBarang)
        )
    }

    private fun konfirmasiHapus(barang: BarangEntity) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Barang")
            .setMessage("Hapus '${barang.namaBarang}' dari daftar inventaris?")
            .setPositiveButton("Hapus") { _, _ -> viewModel.hapusBarang(barang) }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = "Cari nama, kode, atau kategori..."
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = true
            override fun onQueryTextChange(newText: String?): Boolean {
                kataKunci = newText.orEmpty()
                terapkanFilter()
                return true
            }
        })

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_tambah_stok -> {
                startActivity(Intent(this, TambahStokActivity::class.java))
                true
            }
            R.id.action_riwayat -> {
                startActivity(Intent(this, RiwayatTransaksiActivity::class.java))
                true
            }
            R.id.action_laporan -> {
                startActivity(Intent(this, LaporanActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
