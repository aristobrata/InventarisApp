package com.inventaris.peminjaman.repository

import androidx.room.withTransaction
import com.inventaris.peminjaman.data.AppDatabase
import com.inventaris.peminjaman.data.entity.BarangEntity
import com.inventaris.peminjaman.data.entity.JenisTransaksi
import com.inventaris.peminjaman.data.entity.StatusTransaksi
import com.inventaris.peminjaman.data.entity.StokMasukEntity
import com.inventaris.peminjaman.data.entity.TipeBarang
import com.inventaris.peminjaman.data.entity.TransaksiPinjamEntity
import kotlinx.coroutines.flow.Flow

/**
 * Hasil operasi peminjaman/pengembalian, dipakai ViewModel untuk
 * menampilkan pesan sukses/gagal ke UI tanpa exception mentah.
 */
sealed class HasilOperasi {
    data class Sukses(val pesan: String) : HasilOperasi()
    data class Gagal(val pesan: String) : HasilOperasi()
}

class InventarisRepository(private val db: AppDatabase) {

    private val barangDao = db.barangDao()
    private val transaksiDao = db.transaksiDao()
    private val stokMasukDao = db.stokMasukDao()

    // ---------- Master Barang (CRUD) ----------

    fun getAllBarang(): Flow<List<BarangEntity>> = barangDao.getAllBarang()

    fun cariBarang(keyword: String): Flow<List<BarangEntity>> = barangDao.cariBarang(keyword)

    suspend fun getAllBarangOnce(): List<BarangEntity> = barangDao.getAllBarangOnce()

    suspend fun tambahBarang(barang: BarangEntity): HasilOperasi {
        return try {
            // Saat barang baru dibuat: stok_tersedia harus sama dengan total_stok,
            // dan stok_dipinjam/hilang/rusak dimulai dari 0.
            val entitasBaru = barang.copy(
                stokTersedia = barang.totalStok,
                stokDipinjam = 0,
                stokHilang = 0,
                stokRusak = 0,
                stokTerpakai = 0
            )
            barangDao.insert(entitasBaru)
            HasilOperasi.Sukses("Barang berhasil ditambahkan")
        } catch (e: Exception) {
            HasilOperasi.Gagal("Gagal menambah barang (kode barang mungkin sudah dipakai): ${e.message}")
        }
    }

    suspend fun updateBarang(barang: BarangEntity): HasilOperasi {
        return try {
            barangDao.update(barang)
            HasilOperasi.Sukses("Data barang diperbarui")
        } catch (e: Exception) {
            HasilOperasi.Gagal("Gagal memperbarui barang: ${e.message}")
        }
    }

    suspend fun hapusBarang(barang: BarangEntity): HasilOperasi {
        return try {
            if (barang.stokDipinjam > 0) {
                return HasilOperasi.Gagal("Tidak bisa dihapus: masih ada ${barang.stokDipinjam} unit yang sedang dipinjam")
            }
            barangDao.delete(barang)
            HasilOperasi.Sukses("Barang dihapus")
        } catch (e: Exception) {
            HasilOperasi.Gagal("Gagal menghapus barang (kemungkinan masih punya riwayat transaksi): ${e.message}")
        }
    }

    // ---------- Riwayat transaksi ----------

    fun getAllTransaksi(): Flow<List<TransaksiPinjamEntity>> = transaksiDao.getAllTransaksi()

    fun getTransaksiAktif(): Flow<List<TransaksiPinjamEntity>> =
        transaksiDao.getTransaksiByStatus(StatusTransaksi.DIPINJAM)

    // ---------- LOGIKA UTAMA: PEMINJAMAN ----------

    /**
     * Proses peminjaman barang baru.
     * 1. Cek stok tersedia mencukupi.
     * 2. Kurangi stok_tersedia & tambah stok_dipinjam SECARA ATOMIK.
     * 3. Catat baris baru di transaksi_pinjam berstatus DIPINJAM.
     * Semua langkah dibungkus db.withTransaction {} sehingga jika salah satu
     * gagal, semuanya di-rollback (data tidak pernah setengah-tercatat).
     */
    suspend fun pinjamBarang(
        idBarang: Long,
        namaPeminjam: String,
        jumlah: Int,
        tanggalPinjamMillis: Long
    ): HasilOperasi {
        if (jumlah <= 0) return HasilOperasi.Gagal("Jumlah pinjam harus lebih dari 0")
        if (namaPeminjam.isBlank()) return HasilOperasi.Gagal("Nama peminjam wajib diisi")

        return try {
            db.withTransaction {
                val barang = barangDao.getBarangByIdOnce(idBarang)
                    ?: return@withTransaction HasilOperasi.Gagal("Barang tidak ditemukan")

                if (barang.tipeBarang != TipeBarang.PINJAM) {
                    return@withTransaction HasilOperasi.Gagal(
                        "'${barang.namaBarang}' adalah barang habis pakai, gunakan menu 'Ambil Barang', bukan 'Pinjam'"
                    )
                }

                if (barang.stokTersedia < jumlah) {
                    return@withTransaction HasilOperasi.Gagal(
                        "Stok tidak mencukupi. Tersedia: ${barang.stokTersedia} ${barang.satuan}"
                    )
                }

                val baris = barangDao.pinjamStok(idBarang, jumlah)
                if (baris == 0) {
                    // Guard di query WHERE stok_tersedia >= jumlah gagal terpenuhi
                    // -> kemungkinan stok berubah di antara pengecekan & update.
                    return@withTransaction HasilOperasi.Gagal("Stok berubah, silakan coba lagi")
                }

                transaksiDao.insert(
                    TransaksiPinjamEntity(
                        idBarang = idBarang,
                        namaPeminjam = namaPeminjam.trim(),
                        tanggalPinjam = tanggalPinjamMillis,
                        tanggalKembali = null,
                        jumlahPinjam = jumlah,
                        status = StatusTransaksi.DIPINJAM,
                        catatan = null,
                        jenisTransaksi = JenisTransaksi.PEMINJAMAN
                    )
                )

                HasilOperasi.Sukses("Peminjaman '${barang.namaBarang}' sebanyak $jumlah ${barang.satuan} berhasil dicatat")
            }
        } catch (e: Exception) {
            HasilOperasi.Gagal("Gagal memproses peminjaman: ${e.message}")
        }
    }

    // ---------- LOGIKA UTAMA: PENGEMBALIAN ----------

    /**
     * Pengembalian NORMAL (barang utuh, tidak hilang/rusak).
     * stok_dipinjam berkurang, stok_tersedia bertambah kembali.
     */
    suspend fun kembalikanBarangNormal(idTransaksi: Long, catatan: String?): HasilOperasi =
        prosesPengembalian(
            idTransaksi = idTransaksi,
            statusBaru = StatusTransaksi.DIKEMBALIKAN,
            catatan = catatan,
            updateStok = { idBarang, jumlah -> barangDao.kembalikanNormalStok(idBarang, jumlah) }
        )

    /**
     * Pengembalian barang HILANG.
     * stok_dipinjam berkurang, stok_hilang bertambah, total_stok ikut berkurang.
     */
    suspend fun kembalikanBarangHilang(idTransaksi: Long, catatan: String?): HasilOperasi =
        prosesPengembalian(
            idTransaksi = idTransaksi,
            statusBaru = StatusTransaksi.HILANG,
            catatan = catatan ?: "Barang dinyatakan hilang",
            updateStok = { idBarang, jumlah -> barangDao.kembalikanHilangStok(idBarang, jumlah) }
        )

    /**
     * Pengembalian barang RUSAK (rusak parah / tidak bisa dipakai lagi).
     * stok_dipinjam berkurang, stok_rusak bertambah, total_stok ikut berkurang.
     */
    suspend fun kembalikanBarangRusak(idTransaksi: Long, catatan: String?): HasilOperasi =
        prosesPengembalian(
            idTransaksi = idTransaksi,
            statusBaru = StatusTransaksi.RUSAK,
            catatan = catatan ?: "Barang dinyatakan rusak",
            updateStok = { idBarang, jumlah -> barangDao.kembalikanRusakStok(idBarang, jumlah) }
        )

    /**
     * Fungsi inti bersama untuk ke-3 jenis pengembalian di atas, supaya logika
     * validasi + pencatatan transaksi tidak diduplikasi 3 kali.
     */
    private suspend fun prosesPengembalian(
        idTransaksi: Long,
        statusBaru: String,
        catatan: String?,
        updateStok: suspend (idBarang: Long, jumlah: Int) -> Int
    ): HasilOperasi {
        return try {
            db.withTransaction {
                val transaksi = transaksiDao.getByIdOnce(idTransaksi)
                    ?: return@withTransaction HasilOperasi.Gagal("Transaksi tidak ditemukan")

                if (transaksi.status != StatusTransaksi.DIPINJAM) {
                    return@withTransaction HasilOperasi.Gagal(
                        "Transaksi ini sudah selesai diproses sebelumnya (status: ${transaksi.status})"
                    )
                }

                val baris = updateStok(transaksi.idBarang, transaksi.jumlahPinjam)
                if (baris == 0) {
                    return@withTransaction HasilOperasi.Gagal(
                        "Gagal memperbarui stok barang (data tidak konsisten, periksa stok_dipinjam)"
                    )
                }

                transaksiDao.update(
                    transaksi.copy(
                        status = statusBaru,
                        tanggalKembali = System.currentTimeMillis(),
                        catatan = catatan
                    )
                )

                HasilOperasi.Sukses("Pengembalian berhasil dicatat dengan status $statusBaru")
            }
        } catch (e: Exception) {
            HasilOperasi.Gagal("Gagal memproses pengembalian: ${e.message}")
        }
    }

    // ---------- LOGIKA UTAMA: PENGAMBILAN BARANG HABIS PAKAI ----------

    /**
     * Proses pengambilan barang tipe HABIS_PAKAI (consumable). Berbeda dari
     * peminjaman: TIDAK ADA proses kembali. Begitu diambil, stok_tersedia &
     * total_stok langsung berkurang permanen, dan stok_terpakai bertambah.
     * Riwayat tetap dicatat di tabel yang sama (transaksi_pinjam) dengan
     * jenisTransaksi = PENGAMBILAN dan status = DIAMBIL (status akhir/final).
     */
    suspend fun ambilBarangHabisPakai(
        idBarang: Long,
        namaPengambil: String,
        jumlah: Int,
        tanggalMillis: Long,
        catatan: String?
    ): HasilOperasi {
        if (jumlah <= 0) return HasilOperasi.Gagal("Jumlah pengambilan harus lebih dari 0")
        if (namaPengambil.isBlank()) return HasilOperasi.Gagal("Nama pengambil wajib diisi")

        return try {
            db.withTransaction {
                val barang = barangDao.getBarangByIdOnce(idBarang)
                    ?: return@withTransaction HasilOperasi.Gagal("Barang tidak ditemukan")

                if (barang.tipeBarang != TipeBarang.HABIS_PAKAI) {
                    return@withTransaction HasilOperasi.Gagal(
                        "'${barang.namaBarang}' adalah barang yang dipinjamkan, gunakan menu 'Pinjam', bukan 'Ambil'"
                    )
                }

                if (barang.stokTersedia < jumlah) {
                    return@withTransaction HasilOperasi.Gagal(
                        "Stok tidak mencukupi. Tersedia: ${barang.stokTersedia} ${barang.satuan}"
                    )
                }

                val baris = barangDao.ambilHabisPakaiStok(idBarang, jumlah)
                if (baris == 0) {
                    return@withTransaction HasilOperasi.Gagal("Stok berubah, silakan coba lagi")
                }

                transaksiDao.insert(
                    TransaksiPinjamEntity(
                        idBarang = idBarang,
                        namaPeminjam = namaPengambil.trim(),
                        tanggalPinjam = tanggalMillis,
                        tanggalKembali = null, // tidak pernah kembali, sifatnya permanen
                        jumlahPinjam = jumlah,
                        status = StatusTransaksi.DIAMBIL,
                        catatan = catatan,
                        jenisTransaksi = JenisTransaksi.PENGAMBILAN
                    )
                )

                HasilOperasi.Sukses(
                    "Pengambilan '${barang.namaBarang}' sebanyak $jumlah ${barang.satuan} berhasil dicatat"
                )
            }
        } catch (e: Exception) {
            HasilOperasi.Gagal("Gagal memproses pengambilan: ${e.message}")
        }
    }

    // ---------- LOGIKA UTAMA: PENAMBAHAN STOK ----------

    fun getRiwayatStokMasuk(): Flow<List<StokMasukEntity>> = stokMasukDao.getAllRiwayatStokMasuk()

    /**
     * Menambah stok barang (mis. pengadaan/pembelian baru). Berlaku untuk
     * KEDUA tipe barang (Pinjam maupun Habis Pakai). total_stok & stok_tersedia
     * sama-sama bertambah, dan dicatat sebagai riwayat di tabel stok_masuk
     * agar ada jejak audit "kapan & berapa banyak" stok ditambah.
     */
    suspend fun tambahStokBarang(
        idBarang: Long,
        jumlahTambah: Int,
        tanggalMillis: Long,
        keterangan: String?
    ): HasilOperasi {
        if (jumlahTambah <= 0) return HasilOperasi.Gagal("Jumlah tambah stok harus lebih dari 0")

        return try {
            db.withTransaction {
                val barang = barangDao.getBarangByIdOnce(idBarang)
                    ?: return@withTransaction HasilOperasi.Gagal("Barang tidak ditemukan")

                val baris = barangDao.tambahStok(idBarang, jumlahTambah)
                if (baris == 0) {
                    return@withTransaction HasilOperasi.Gagal("Gagal menambah stok, barang mungkin sudah dihapus")
                }

                stokMasukDao.insert(
                    StokMasukEntity(
                        idBarang = idBarang,
                        jumlah = jumlahTambah,
                        tanggal = tanggalMillis,
                        keterangan = keterangan
                    )
                )

                HasilOperasi.Sukses(
                    "Stok '${barang.namaBarang}' berhasil ditambah $jumlahTambah ${barang.satuan} " +
                            "(total sekarang: ${barang.totalStok + jumlahTambah} ${barang.satuan})"
                )
            }
        } catch (e: Exception) {
            HasilOperasi.Gagal("Gagal menambah stok: ${e.message}")
        }
    }
}
