package com.inventaris.peminjaman.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.inventaris.peminjaman.data.dao.BarangDao
import com.inventaris.peminjaman.data.dao.StokMasukDao
import com.inventaris.peminjaman.data.dao.TransaksiDao
import com.inventaris.peminjaman.data.entity.BarangEntity
import com.inventaris.peminjaman.data.entity.StokMasukEntity
import com.inventaris.peminjaman.data.entity.TransaksiPinjamEntity

@Database(
    entities = [BarangEntity::class, TransaksiPinjamEntity::class, StokMasukEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun barangDao(): BarangDao
    abstract fun transaksiDao(): TransaksiDao
    abstract fun stokMasukDao(): StokMasukDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v1 -> v2: menambah fitur "Tipe Barang" (Pinjam / Habis Pakai),
         * kolom stok_terpakai, jenis_transaksi pada riwayat transaksi, dan
         * tabel baru stok_masuk (riwayat penambahan stok). Ditulis eksplisit
         * (bukan hanya mengandalkan fallbackToDestructiveMigration) supaya
         * data barang & transaksi yang sudah ada TIDAK hilang saat update.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE barang ADD COLUMN stok_terpakai INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE barang ADD COLUMN tipe_barang TEXT NOT NULL DEFAULT 'PINJAM'"
                )
                db.execSQL(
                    "ALTER TABLE transaksi_pinjam ADD COLUMN jenis_transaksi TEXT NOT NULL DEFAULT 'PEMINJAMAN'"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS stok_masuk (
                        id_stok_masuk INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        id_barang INTEGER NOT NULL,
                        jumlah INTEGER NOT NULL,
                        tanggal INTEGER NOT NULL,
                        keterangan TEXT,
                        FOREIGN KEY(id_barang) REFERENCES barang(id_barang) ON UPDATE CASCADE ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_stok_masuk_id_barang ON stok_masuk(id_barang)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "inventaris_peminjaman.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    // Jaring pengaman terakhir jika suatu saat ada versi tanpa
                    // migration path eksplisit (mis. saat development cepat).
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
