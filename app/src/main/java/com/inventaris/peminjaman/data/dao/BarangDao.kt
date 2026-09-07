package com.inventaris.peminjaman.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.inventaris.peminjaman.data.entity.BarangEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BarangDao {

    // ---------- CRUD dasar ----------

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(barang: BarangEntity): Long

    @Update
    suspend fun update(barang: BarangEntity)

    @Delete
    suspend fun delete(barang: BarangEntity)

    @Query("SELECT * FROM barang ORDER BY nama_barang ASC")
    fun getAllBarang(): Flow<List<BarangEntity>>

    @Query("SELECT * FROM barang WHERE id_barang = :idBarang")
    fun getBarangByIdFlow(idBarang: Long): Flow<BarangEntity?>

    @Query("SELECT * FROM barang WHERE id_barang = :idBarang")
    suspend fun getBarangByIdOnce(idBarang: Long): BarangEntity?

    @Query(
        """
        SELECT * FROM barang
        WHERE nama_barang LIKE '%' || :keyword || '%'
           OR kode_barang LIKE '%' || :keyword || '%'
           OR kategori LIKE '%' || :keyword || '%'
        ORDER BY nama_barang ASC
        """
    )
    fun cariBarang(keyword: String): Flow<List<BarangEntity>>

    @Query("SELECT * FROM barang ORDER BY nama_barang ASC")
    suspend fun getAllBarangOnce(): List<BarangEntity>

    // ---------- Update stok ATOMIK ----------
    // Dijalankan langsung sebagai UPDATE di level SQL (bukan read-modify-write di
    // Kotlin) agar aman dari race condition bila dipakai bersamaan / berulang cepat.
    // Klausa WHERE tambahan (mis. stok_tersedia >= :jumlah) berfungsi sebagai
    // guard: jika baris yang ter-update = 0, berarti stok tidak mencukupi lagi.

    /** Dipakai saat PEMINJAMAN baru: tersedia berkurang, dipinjam bertambah. */
    @Query(
        """
        UPDATE barang
        SET stok_tersedia = stok_tersedia - :jumlah,
            stok_dipinjam = stok_dipinjam + :jumlah
        WHERE id_barang = :idBarang AND stok_tersedia >= :jumlah
        """
    )
    suspend fun pinjamStok(idBarang: Long, jumlah: Int): Int

    /** Dipakai saat PENGEMBALIAN NORMAL: dipinjam berkurang, tersedia bertambah. */
    @Query(
        """
        UPDATE barang
        SET stok_dipinjam = stok_dipinjam - :jumlah,
            stok_tersedia = stok_tersedia + :jumlah
        WHERE id_barang = :idBarang AND stok_dipinjam >= :jumlah
        """
    )
    suspend fun kembalikanNormalStok(idBarang: Long, jumlah: Int): Int

    /** Dipakai saat barang dinyatakan HILANG: dipinjam & total stok berkurang. */
    @Query(
        """
        UPDATE barang
        SET stok_dipinjam = stok_dipinjam - :jumlah,
            stok_hilang = stok_hilang + :jumlah,
            total_stok = total_stok - :jumlah
        WHERE id_barang = :idBarang AND stok_dipinjam >= :jumlah
        """
    )
    suspend fun kembalikanHilangStok(idBarang: Long, jumlah: Int): Int

    /** Dipakai saat barang dinyatakan RUSAK: dipinjam & total stok berkurang. */
    @Query(
        """
        UPDATE barang
        SET stok_dipinjam = stok_dipinjam - :jumlah,
            stok_rusak = stok_rusak + :jumlah,
            total_stok = total_stok - :jumlah
        WHERE id_barang = :idBarang AND stok_dipinjam >= :jumlah
        """
    )
    suspend fun kembalikanRusakStok(idBarang: Long, jumlah: Int): Int

    /**
     * Dipakai saat PENAMBAHAN STOK baru (mis. pengadaan/pembelian barang):
     * total_stok & stok_tersedia sama-sama bertambah.
     */
    @Query(
        """
        UPDATE barang
        SET total_stok = total_stok + :jumlah,
            stok_tersedia = stok_tersedia + :jumlah
        WHERE id_barang = :idBarang
        """
    )
    suspend fun tambahStok(idBarang: Long, jumlah: Int): Int

    /**
     * Dipakai saat barang tipe HABIS_PAKAI diambil: stok_tersedia & total_stok
     * berkurang permanen, stok_terpakai bertambah (tidak ada proses kembali).
     */
    @Query(
        """
        UPDATE barang
        SET stok_tersedia = stok_tersedia - :jumlah,
            stok_terpakai = stok_terpakai + :jumlah,
            total_stok = total_stok - :jumlah
        WHERE id_barang = :idBarang AND stok_tersedia >= :jumlah
        """
    )
    suspend fun ambilHabisPakaiStok(idBarang: Long, jumlah: Int): Int
}
