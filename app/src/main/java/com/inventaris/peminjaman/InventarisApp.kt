package com.inventaris.peminjaman

import androidx.multidex.MultiDexApplication
import com.inventaris.peminjaman.data.AppDatabase
import com.inventaris.peminjaman.repository.InventarisRepository

/**
 * MultiDexApplication dipakai karena Apache POI + xmlbeans + commons-compress
 * menambah cukup banyak jumlah method, berpotensi melewati batas 65K method
 * pada beberapa konfigurasi minSdk lama.
 */
class InventarisApp : MultiDexApplication() {

    // Singleton sederhana. Untuk proyek skala lebih besar, ganti dengan
    // Dependency Injection (Hilt/Koin).
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: InventarisRepository by lazy { InventarisRepository(database) }
}
