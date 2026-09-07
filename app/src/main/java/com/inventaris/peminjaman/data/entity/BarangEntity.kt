package com.inventaris.peminjaman.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Master data barang / inventaris.
 *
 * Ada dua TIPE barang (lihat [TipeBarang]):
 *  - PINJAM      : alat/barang yang dipinjamkan dan WAJIB dikembalikan
 *                   (mis. bor listrik, proyektor, kursi lipat).
 *  - HABIS_PAKAI : barang consumable yang diambil dan TIDAK dikembalikan
 *                   (mis. baterai, tinta printer, ATK).
 *
 * Relasi antar kolom stok yang WAJIB selalu konsisten:
 *   totalStok = stokTersedia + stokDipinjam + stokHilang + stokRusak + stokTerpakai
 *
 * Kolom stokHilang, stokRusak, & stokTerpakai bersifat AKUMULATIF (riwayat
 * sepanjang waktu), bukan hanya kondisi transaksi terakhir.
 */
@Entity(
    tableName = "barang",
    indices = [Index(value = ["kode_barang"], unique = true)]
)
data class BarangEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id_barang")
    val idBarang: Long = 0,

    @ColumnInfo(name = "kode_barang")
    val kodeBarang: String,

    @ColumnInfo(name = "nama_barang")
    val namaBarang: String,

    @ColumnInfo(name = "kategori")
    val kategori: String,

    @ColumnInfo(name = "total_stok")
    val totalStok: Int,

    @ColumnInfo(name = "stok_tersedia")
    val stokTersedia: Int,

    @ColumnInfo(name = "stok_dipinjam")
    val stokDipinjam: Int = 0,

    @ColumnInfo(name = "stok_hilang")
    val stokHilang: Int = 0,

    @ColumnInfo(name = "stok_rusak")
    val stokRusak: Int = 0,

    /** Akumulasi stok yang sudah diambil & dipakai habis (khusus tipe HABIS_PAKAI). */
    @ColumnInfo(name = "stok_terpakai", defaultValue = "0")
    val stokTerpakai: Int = 0,

    @ColumnInfo(name = "satuan")
    val satuan: String,

    /** Nilai dari [TipeBarang]. Default PINJAM agar data lama tetap valid. */
    @ColumnInfo(name = "tipe_barang", defaultValue = "'PINJAM'")
    val tipeBarang: String = TipeBarang.PINJAM
)

/** Konstanta tipe barang: menentukan alur transaksi mana yang berlaku. */
object TipeBarang {
    /** Dipinjamkan, wajib dikembalikan. */
    const val PINJAM = "PINJAM"

    /** Diambil & dipakai habis, tidak dikembalikan. */
    const val HABIS_PAKAI = "HABIS_PAKAI"

    /** Bisa dipinjamkan DAN bisa dipakai habis. */
    const val KEDUANYA = "KEDUANYA"
}
