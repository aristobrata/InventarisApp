package com.inventaris.peminjaman.data.entity

/**
 * Status transaksi peminjaman/pengambilan.
 *
 * Catatan penyesuaian dari spesifikasi awal:
 * Spesifikasi menyebutkan status "HILANG/RUSAK PARAH" sebagai satu nilai gabungan.
 * Di implementasi ini nilai tersebut dipecah menjadi HILANG dan RUSAK secara
 * terpisah, karena efeknya ke stok ('stok_hilang' vs 'stok_rusak') berbeda dan
 * akan sulit dibedakan lagi kalau digabung jadi satu string. Silakan gabungkan
 * kembali di layer UI (mis. tampilkan sebagai "Bermasalah") jika diperlukan.
 */
object StatusTransaksi {
    const val DIPINJAM = "DIPINJAM"
    const val DIKEMBALIKAN = "DIKEMBALIKAN"
    const val HILANG = "HILANG"
    const val RUSAK = "RUSAK"

    /** Status akhir untuk transaksi jenis PENGAMBILAN (barang habis pakai, tidak kembali). */
    const val DIAMBIL = "DIAMBIL"
}

/** Jenis transaksi: menentukan apakah barang WAJIB kembali atau tidak. */
object JenisTransaksi {
    /** Barang dipinjamkan, wajib dikembalikan (normal/hilang/rusak). */
    const val PEMINJAMAN = "PEMINJAMAN"

    /** Barang diambil & dipakai habis, tidak ada proses pengembalian. */
    const val PENGAMBILAN = "PENGAMBILAN"
}
