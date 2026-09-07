package com.inventaris.peminjaman.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.inventaris.peminjaman.data.entity.BarangEntity
import com.inventaris.peminjaman.repository.HasilOperasi
import com.inventaris.peminjaman.repository.InventarisRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(private val repository: InventarisRepository) : ViewModel() {

    val daftarBarang: StateFlow<List<BarangEntity>> = repository.getAllBarang()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _pesan = MutableSharedFlow<String>()
    val pesan: SharedFlow<String> = _pesan

    fun hapusBarang(barang: BarangEntity) {
        viewModelScope.launch {
            val hasil = repository.hapusBarang(barang)
            emitPesan(hasil)
        }
    }

    private suspend fun emitPesan(hasil: HasilOperasi) {
        val teks = when (hasil) {
            is HasilOperasi.Sukses -> hasil.pesan
            is HasilOperasi.Gagal -> "Gagal: ${hasil.pesan}"
        }
        _pesan.emit(teks)
    }

    class Factory(private val repository: InventarisRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository) as T
        }
    }
}
