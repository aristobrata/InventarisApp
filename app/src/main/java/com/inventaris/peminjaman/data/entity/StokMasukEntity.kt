package com.inventaris.peminjaman.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Riwayat setiap kali stok sebuah barang ditambah (mis. pembelian baru,
 * pengadaan ulang). Dipakai untuk audit trail: "kapan & berapa banyak stok
 * masuk", terpisah dari riwayat peminjaman/pengambilan.
 */
@Entity(
    tableName = "stok_masuk",
    foreignKeys = [
        ForeignKey(
            entity = BarangEntity::class,
            parentColumns = ["id_barang"],
            childColumns = ["id_barang"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["id_barang"])]
)
data class StokMasukEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id_stok_masuk")
    val idStokMasuk: Long = 0,

    @ColumnInfo(name = "id_barang")
    val idBarang: Long,

    @ColumnInfo(name = "jumlah")
    val jumlah: Int,

    /** Epoch millis. */
    @ColumnInfo(name = "tanggal")
    val tanggal: Long,

    @ColumnInfo(name = "keterangan")
    val keterangan: String? = null
)
