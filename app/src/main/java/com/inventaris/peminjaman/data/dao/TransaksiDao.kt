package com.inventaris.peminjaman.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.inventaris.peminjaman.data.entity.TransaksiPinjamEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransaksiDao {

    @Insert
    suspend fun insert(transaksi: TransaksiPinjamEntity): Long

    @Update
    suspend fun update(transaksi: TransaksiPinjamEntity)

    @Query("SELECT * FROM transaksi_pinjam WHERE id_transaksi = :id")
    suspend fun getByIdOnce(id: Long): TransaksiPinjamEntity?

    @Query("SELECT * FROM transaksi_pinjam ORDER BY tanggal_pinjam DESC")
    fun getAllTransaksi(): Flow<List<TransaksiPinjamEntity>>

    @Query("SELECT * FROM transaksi_pinjam WHERE status = :status ORDER BY tanggal_pinjam DESC")
    fun getTransaksiByStatus(status: String): Flow<List<TransaksiPinjamEntity>>

    @Query("SELECT * FROM transaksi_pinjam WHERE id_barang = :idBarang ORDER BY tanggal_pinjam DESC")
    fun getTransaksiByBarang(idBarang: Long): Flow<List<TransaksiPinjamEntity>>
}
