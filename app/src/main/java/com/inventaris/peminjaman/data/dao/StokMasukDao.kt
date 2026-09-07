package com.inventaris.peminjaman.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.inventaris.peminjaman.data.entity.StokMasukEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StokMasukDao {

    @Insert
    suspend fun insert(stokMasuk: StokMasukEntity): Long

    @Query("SELECT * FROM stok_masuk ORDER BY tanggal DESC")
    fun getAllRiwayatStokMasuk(): Flow<List<StokMasukEntity>>

    @Query("SELECT * FROM stok_masuk WHERE id_barang = :idBarang ORDER BY tanggal DESC")
    fun getRiwayatStokMasukByBarang(idBarang: Long): Flow<List<StokMasukEntity>>
}
