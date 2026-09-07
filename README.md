# Sistem Peminjaman & Pengembalian Inventaris Barang (Offline)

Aplikasi Android (Kotlin) 100% offline berbasis Room (abstraksi SQLite) untuk
mengelola peminjaman & pengembalian barang inventaris, lengkap dengan ekspor
laporan ke file Excel (.xlsx) memakai Apache POI.

## 1. Cara membuka di Android Studio

1. Ekstrak file zip ini.
2. Buka Android Studio → **File > Open** → pilih folder hasil ekstrak (folder yang berisi `settings.gradle.kts`).
3. Android Studio otomatis mendeteksi ini proyek Gradle dan akan menawarkan membuat **Gradle Wrapper** — klik OK / biarkan proses sinkronisasi berjalan.
   (File `gradlew` / `gradlew.bat` / `gradle-wrapper.jar` sengaja tidak disertakan dalam zip karena berupa berkas biner; Android Studio akan meregenerasinya otomatis saat sinkronisasi pertama, atau Anda bisa menjalankan `gradle wrapper` sekali secara manual jika memakai Gradle yang sudah terpasang.)
4. Tunggu proses **Gradle Sync** selesai (butuh koneksi internet untuk mengunduh dependency Room, Apache POI, dsb. — setelah itu aplikasi berjalan 100% offline).
5. Jalankan ke emulator/device dengan tombol **Run ▶** (`app` module, minSdk 26 / Android 8.0 Oreo — lihat catatan di bagian 5 kenapa minSdk tidak bisa lebih rendah).

## 2. Struktur Proyek

```
app/src/main/java/com/inventaris/peminjaman/
├── InventarisApp.kt                  # Application class (singleton DB + Repository)
├── data/
│   ├── entity/BarangEntity.kt
│   ├── entity/TransaksiPinjamEntity.kt
│   ├── entity/StatusTransaksi.kt     # konstanta status transaksi
│   ├── dao/BarangDao.kt              # CRUD + query UPDATE stok atomik
│   ├── dao/TransaksiDao.kt
│   └── AppDatabase.kt                # RoomDatabase singleton
├── repository/
│   └── InventarisRepository.kt       # LOGIKA BISNIS pinjam & kembali (inti sistem)
├── util/
│   └── ExcelExporter.kt              # Ekspor .xlsx dengan Apache POI + MediaStore
└── ui/
    ├── main/                         # Daftar barang + tombol Ekspor Excel
    ├── barang/                       # Form tambah/edit barang (CRUD)
    └── transaksi/                    # Form pinjam & layar riwayat/pengembalian
```

## 3. Ringkasan Logika Bisnis (di `InventarisRepository.kt`)

Semua perubahan stok dijalankan sebagai **satu database transaction**
(`db.withTransaction { ... }`) sehingga tidak mungkin terjadi data
"setengah tercatat" (misal stok sudah berubah tapi riwayat transaksi gagal
tersimpan, atau sebaliknya).

Setiap barang punya **Tipe Barang** yang menentukan alur mana yang berlaku:

| Tipe Barang | Alur yang berlaku | Kembali? |
|---|---|---|
| **PINJAM** (Dipinjamkan) | Pinjam → Kembali (Normal/Hilang/Rusak) | Ya, wajib |
| **HABIS_PAKAI** (Consumable) | Ambil | Tidak, permanen |

| Aksi | Efek ke `BarangEntity` |
|---|---|
| **Pinjam baru** | `stok_tersedia -= jumlah`, `stok_dipinjam += jumlah` |
| **Kembali normal** | `stok_dipinjam -= jumlah`, `stok_tersedia += jumlah` |
| **Kembali → Hilang** | `stok_dipinjam -= jumlah`, `stok_hilang += jumlah`, `total_stok -= jumlah` |
| **Kembali → Rusak** | `stok_dipinjam -= jumlah`, `stok_rusak += jumlah`, `total_stok -= jumlah` |
| **Ambil (Habis Pakai)** | `stok_tersedia -= jumlah`, `stok_terpakai += jumlah`, `total_stok -= jumlah` |
| **Tambah Stok** | `total_stok += jumlah`, `stok_tersedia += jumlah` (+ dicatat di tabel `stok_masuk`) |

Update stok dilakukan sebagai satu perintah `UPDATE ... WHERE stok_x >= :jumlah`
langsung di level SQL (bukan baca-ubah-simpan terpisah di Kotlin), supaya aman
dari race condition. Jika baris yang ter-update = 0, artinya stok sudah
berubah/tidak cukup sejak terakhir dicek, dan operasi dibatalkan dengan pesan
error yang jelas.

Repository juga memvalidasi kecocokan tipe barang: barang tipe PINJAM tidak
bisa diproses lewat `ambilBarangHabisPakai()`, dan sebaliknya barang tipe
HABIS_PAKAI tidak bisa diproses lewat `pinjamBarang()` — masing-masing akan
menolak dengan pesan error yang mengarahkan ke menu yang benar.

**Catatan penyesuaian dari spesifikasi:** status `"HILANG/RUSAK PARAH"` yang
awalnya digabung menjadi satu nilai, di implementasi ini dipecah menjadi
`HILANG` dan `RUSAK` terpisah (lihat `StatusTransaksi.kt`), karena efeknya ke
kolom stok berbeda (`stok_hilang` vs `stok_rusak`) dan akan sulit dibedakan
lagi kalau digabung jadi satu string status.

## 4. Fitur Baru (v2)

- **Tipe Barang** — saat menambah/edit barang, pilih "Dipinjamkan" atau
  "Habis Pakai" lewat ChipGroup di `TambahEditBarangActivity`. Tipe ini
  terkunci otomatis begitu barang punya riwayat transaksi apa pun, supaya
  laporan lama tidak jadi ambigu.
- **Ambil Barang Habis Pakai** (`AmbilBarangActivity`) — alur terpisah dari
  peminjaman, tanpa proses kembali; dicatat dengan `jenisTransaksi =
  PENGAMBILAN` dan `status = DIAMBIL` (status final).
- **Tambah Stok** (`TambahStokActivity`) — form untuk mencatat pengadaan/
  pembelian barang baru; setiap penambahan tercatat di tabel `stok_masuk`
  sebagai jejak audit (kapan, berapa banyak, keterangan/no. faktur).
- **Laporan Excel** (`LaporanActivity`) — pusat ekspor, terpisah jadi dua:
  - *Laporan Stok Barang*: snapshot kondisi stok seluruh barang saat ini.
  - *Laporan Transaksi*: riwayat peminjaman & pengambilan, dengan filter
    jenis transaksi dan status sebelum diekspor.
- **UI diperbarui** — kartu barang dengan badge tipe barang & bar rasio
  stok berwarna (hijau/kuning/merah), filter chip di halaman utama &
  riwayat transaksi, kartu ringkasan statistik di dashboard, dan search bar
  di toolbar.

## 5. Ekspor Excel (`ExcelExporter.kt`)

- Menggunakan **Apache POI** (`poi-ooxml`) untuk membangun file `.xlsx`.
- Judul laporan + tanggal cetak otomatis di baris paling atas.
- Header tabel diberi warna latar & border.
- Baris **TOTAL** paling bawah berisi formula Excel asli `=SUM(...)`
  untuk setiap kolom angka (Total Stok, Tersedia, Dipinjam, Hilang, Rusak) —
  jadi kalau file dibuka & datanya diedit manual di Excel, angka total ikut
  ter-update otomatis.
- Lebar kolom diatur **manual** (bukan `Sheet#autoSizeColumn()`), karena
  `autoSizeColumn()` bergantung pada `java.awt.Font` yang tidak tersedia
  penuh di runtime Android dan akan menyebabkan crash
  (`NoClassDefFoundError`) jika dipakai langsung di device.
- File disimpan ke folder **Downloads** memakai `MediaStore` (Scoped
  Storage API 29+), sehingga **tidak perlu permission runtime**
  `WRITE_EXTERNAL_STORAGE` di Android 10 ke atas. Ada fallback untuk
  Android 9 ke bawah (lihat komentar di kode).

## 6. Catatan Penting seputar Apache POI di Android

`poi-ooxml` awalnya dirancang untuk JVM desktop/server, bukan Android, dan
membawa dependency yang cukup berat (`xmlbeans`, `commons-compress`, dll).
Beberapa hal yang sudah diantisipasi di proyek ini:

- **`minSdk` wajib >= 26 (Android 8.0 Oreo).** Kelas
  `org.apache.poi.poifs.nio.CleanerUtil` di dalam POI memakai
  `MethodHandle.invoke` / `invokeExact` (invoke-polymorphic), fitur bahasa
  Java yang baru didukung Dalvik/ART mulai API 26. Jika `minSdk` diturunkan
  di bawah itu, proses dexing (D8) akan gagal dengan error:
  `"MethodHandle.invoke and MethodHandle.invokeExact are only supported
  starting with Android O (--min-api 26)"`. Tidak ada cara aman untuk tetap
  memakai `poi-ooxml` di API < 26 — satu-satunya solusi lain adalah mengganti
  library ke penulis `.xlsx` yang lebih ringan/khusus-Android (lihat poin
  terakhir di bawah).
- **Jumlah method (65K limit)** → `multiDexEnabled = true` di `build.gradle.kts`
  + `InventarisApp` diturunkan dari `MultiDexApplication`. (Sejak minSdk 21+,
  D8 sebenarnya sudah mendukung multidex native secara otomatis, tapi
  konfigurasi ini tetap dipertahankan sebagai jaring pengaman.)
- **`java.awt.*` tidak tersedia di Android** → hindari API POI yang
  menyentuh AWT, seperti `autoSizeColumn()` (sudah diganti perhitungan lebar
  kolom manual) dan fitur gambar/chart POI yang butuh `java.awt.image`.
- **Konflik file `META-INF/...` saat packaging** → sudah ditangani lewat
  blok `packaging { resources { excludes += ... } }`.
- Jika di device tertentu masih muncul error class-not-found terkait POI,
  solusi paling stabil adalah menaikkan `minSdk` dan memastikan
  `coreLibraryDesugaring` aktif (sudah diaktifkan di proyek ini), atau
  sebagai alternatif jangka panjang mempertimbangkan library penulis
  `.xlsx` yang lebih ringan khusus Android (mis. FastExcel) bila suatu saat
  POI terbukti bermasalah di perangkat target Anda.

## 7. Yang Perlu Anda Lengkapi Sendiri

Proyek ini sudah bisa di-build dan dijalankan (CRUD barang, tipe barang
pinjam/habis-pakai, pinjam, kembalikan normal/hilang/rusak, ambil habis
pakai, tambah stok, ekspor Laporan Stok & Laporan Transaksi ke Excel), tapi
masih ada ruang pengembangan lebih lanjut:
- Filter tanggal (dari-sampai) pada Laporan Transaksi, saat ini filternya baru jenis & status.
- Riwayat "Tambah Stok" belum punya layar tersendiri untuk ditampilkan ke user (datanya sudah tersimpan di tabel `stok_masuk`, tinggal dibuatkan Activity/RecyclerView-nya jika diperlukan).
- Dialog konfirmasi jumlah pengembalian parsial (saat ini 1 transaksi = 1 kali kembalikan penuh sesuai jumlah_pinjam).
- Unit test untuk `InventarisRepository` (skenario stok tidak cukup, race condition, validasi tipe barang, dll).
- Ikon aplikasi & kartu barang saat ini memakai ikon bawaan Android (`@android:drawable/...`) supaya bebas risiko error vector; bisa diganti dengan ikon custom (mis. dari Material Icons) untuk polish visual lebih lanjut.
