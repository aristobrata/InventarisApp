package com.inventaris.peminjaman.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Riwayat / catatan setiap transaksi peminjaman barang.
 * TIDAK ADA kolom harga / nilai uang sama sekali, sesuai spesifikasi.
 */
@Entity(
    tableName = "transaksi_pinjam",
    foreignKeys = [
        ForeignKey(
            entity = BarangEntity::class,
            parentColumns = ["id_barang"],
            childColumns = ["id_barang"],
            onDelete = ForeignKey.RESTRICT, // Cegah barang terhapus jika masih punya riwayat transaksi
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["id_barang"]), Index(value = ["status"])]
)
data class TransaksiPinjamEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id_transaksi")
    val idTransaksi: Long = 0,

    @ColumnInfo(name = "id_barang")
    val idBarang: Long,

    @ColumnInfo(name = "nama_peminjam")
    val namaPeminjam: String,

    /** Disimpan sebagai epoch millis (Long) agar mudah diurutkan & difilter. */
    @ColumnInfo(name = "tanggal_pinjam")
    val tanggalPinjam: Long,

    @ColumnInfo(name = "tanggal_kembali")
    val tanggalKembali: Long? = null,

    @ColumnInfo(name = "jumlah_pinjam")
    val jumlahPinjam: Int,

    @ColumnInfo(name = "status")
    val status: String = StatusTransaksi.DIPINJAM,

    @ColumnInfo(name = "catatan")
    val catatan: String? = null,

    /** Nilai dari [JenisTransaksi]. Default PEMINJAMAN agar data lama tetap valid. */
    @ColumnInfo(name = "jenis_transaksi", defaultValue = "'PEMINJAMAN'")
    val jenisTransaksi: String = JenisTransaksi.PEMINJAMAN
)
