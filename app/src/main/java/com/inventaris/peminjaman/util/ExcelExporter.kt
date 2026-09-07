package com.inventaris.peminjaman.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.inventaris.peminjaman.data.entity.BarangEntity
import com.inventaris.peminjaman.data.entity.JenisTransaksi
import com.inventaris.peminjaman.data.entity.TipeBarang
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.ss.util.CellReference
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFFont
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Satu baris pada Laporan Transaksi Excel. Dibangun oleh caller (biasanya
 * LaporanActivity) dengan menggabungkan TransaksiPinjamEntity + data barang
 * terkait, supaya ExcelExporter tidak perlu bergantung langsung ke Repository/DAO.
 */
data class BarisLaporanTransaksi(
    val kodeBarang: String,
    val namaBarang: String,
    val jenisTransaksi: String, // JenisTransaksi.PEMINJAMAN / PENGAMBILAN
    val namaOrang: String,      // nama peminjam ATAU nama pengambil
    val jumlah: Int,
    val satuan: String,
    val tanggalTransaksiMillis: Long,
    val tanggalKembaliMillis: Long?,
    val status: String,
    val catatan: String?
)

/**
 * Helper untuk mengekspor data ke file .xlsx dan menyimpannya ke folder
 * Downloads memakai MediaStore (Scoped Storage), sehingga TIDAK memerlukan
 * permission WRITE_EXTERNAL_STORAGE di Android 10+ (API 29+).
 *
 * Catatan penting untuk Android:
 * Apache POI (khususnya fitur `Sheet#autoSizeColumn`) bergantung pada
 * java.awt.Font untuk menghitung lebar teks, dan java.awt TIDAK tersedia
 * penuh di runtime Android -> akan crash (NoClassDefFoundError) kalau dipakai.
 * Karena itu, "auto-fit lebar kolom" pada implementasi ini dilakukan secara
 * MANUAL (estimasi lebar dari panjang teks terpanjang per kolom), bukan
 * memanggil autoSizeColumn().
 */
object ExcelExporter {

    private const val MIME_XLSX =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    private val formatTanggalJudul = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("in", "ID"))
    private val formatTanggalSel = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("in", "ID"))

    // =====================================================================
    // 1) LAPORAN STOK BARANG (master data)
    // =====================================================================

    private val HEADER_BARANG = arrayOf(
        "No", "Kode Barang", "Nama Barang", "Kategori", "Tipe Barang", "Satuan",
        "Total Stok", "Stok Tersedia", "Sedang Dipinjam", "Stok Hilang", "Stok Rusak", "Stok Terpakai"
    )

    // Index kolom (0-based) untuk kolom-kolom numerik yang perlu di-SUM.
    private val KOLOM_NUMERIK_BARANG = intArrayOf(6, 7, 8, 9, 10, 11)

    /**
     * @return Uri file hasil ekspor jika berhasil, atau null jika gagal.
     */
    fun exportBarangKeExcel(context: Context, daftarBarang: List<BarangEntity>): Uri? {
        val workbook = XSSFWorkbook()
        try {
            val sheet = workbook.createSheet("Laporan Stok")
            val style = buatSemuaStyle(workbook)

            var baris = tulisJudul(
                sheet, style,
                judul = "LAPORAN INVENTARIS STOK BARANG",
                jumlahKolom = HEADER_BARANG.size
            )

            val baseRowHeader = baris
            val rowHeader = sheet.createRow(baris++)
            HEADER_BARANG.forEachIndexed { i, judul ->
                rowHeader.createCell(i).apply { setCellValue(judul); cellStyle = style.header }
            }

            val baseRowData = baris
            daftarBarang.forEachIndexed { index, b ->
                val row = sheet.createRow(baris++)
                row.createCell(0).apply { setCellValue((index + 1).toDouble()); cellStyle = style.selAngka }
                row.createCell(1).apply { setCellValue(b.kodeBarang); cellStyle = style.sel }
                row.createCell(2).apply { setCellValue(b.namaBarang); cellStyle = style.sel }
                row.createCell(3).apply { setCellValue(b.kategori); cellStyle = style.sel }
                row.createCell(4).apply {
                    setCellValue(if (b.tipeBarang == TipeBarang.HABIS_PAKAI) "Habis Pakai" else "Dipinjamkan")
                    cellStyle = style.sel
                }
                row.createCell(5).apply { setCellValue(b.satuan); cellStyle = style.sel }
                row.createCell(6).apply { setCellValue(b.totalStok.toDouble()); cellStyle = style.selAngka }
                row.createCell(7).apply { setCellValue(b.stokTersedia.toDouble()); cellStyle = style.selAngka }
                row.createCell(8).apply { setCellValue(b.stokDipinjam.toDouble()); cellStyle = style.selAngka }
                row.createCell(9).apply { setCellValue(b.stokHilang.toDouble()); cellStyle = style.selAngka }
                row.createCell(10).apply { setCellValue(b.stokRusak.toDouble()); cellStyle = style.selAngka }
                row.createCell(11).apply { setCellValue(b.stokTerpakai.toDouble()); cellStyle = style.selAngka }
            }
            val baseRowDataTerakhir = baris - 1

            tulisBarisTotal(
                sheet, style,
                baseRowData = baseRowData,
                baseRowDataTerakhir = baseRowDataTerakhir,
                kolomNumerik = KOLOM_NUMERIK_BARANG,
                kolomLabelTotal = 1,
                jumlahKolom = HEADER_BARANG.size,
                adaData = daftarBarang.isNotEmpty(),
                baris = baris
            )

            aturLebarKolomManual(
                sheet,
                header = HEADER_BARANG,
                lebarMinimal = intArrayOf(4, 14, 22, 14, 12, 8, 10, 10, 12, 10, 10, 10),
                kolomTeksIndexDanNilai = daftarBarang.flatMap { b ->
                    listOf(1 to b.kodeBarang, 2 to b.namaBarang, 3 to b.kategori, 5 to b.satuan)
                }
            )

            return simpanKeDownloads(context, workbook, "Laporan_Stok_Inventaris")
        } finally {
            workbook.close()
        }
    }

    // =====================================================================
    // 2) LAPORAN TRANSAKSI (riwayat peminjaman & pengambilan)
    // =====================================================================

    private val HEADER_TRANSAKSI = arrayOf(
        "No", "Kode Barang", "Nama Barang", "Jenis Transaksi", "Nama Peminjam/Pengambil",
        "Jumlah", "Satuan", "Tanggal Transaksi", "Tanggal Kembali", "Status", "Catatan"
    )

    private val KOLOM_NUMERIK_TRANSAKSI = intArrayOf(5)

    /**
     * @return Uri file hasil ekspor jika berhasil, atau null jika gagal.
     */
    fun exportTransaksiKeExcel(context: Context, daftarTransaksi: List<BarisLaporanTransaksi>): Uri? {
        val workbook = XSSFWorkbook()
        try {
            val sheet = workbook.createSheet("Laporan Transaksi")
            val style = buatSemuaStyle(workbook)

            var baris = tulisJudul(
                sheet, style,
                judul = "LAPORAN TRANSAKSI PEMINJAMAN & PENGAMBILAN BARANG",
                jumlahKolom = HEADER_TRANSAKSI.size
            )

            val baseRowHeader = baris
            val rowHeader = sheet.createRow(baris++)
            HEADER_TRANSAKSI.forEachIndexed { i, judul ->
                rowHeader.createCell(i).apply { setCellValue(judul); cellStyle = style.header }
            }

            val baseRowData = baris
            daftarTransaksi.forEachIndexed { index, t ->
                val row = sheet.createRow(baris++)
                row.createCell(0).apply { setCellValue((index + 1).toDouble()); cellStyle = style.selAngka }
                row.createCell(1).apply { setCellValue(t.kodeBarang); cellStyle = style.sel }
                row.createCell(2).apply { setCellValue(t.namaBarang); cellStyle = style.sel }
                row.createCell(3).apply {
                    setCellValue(if (t.jenisTransaksi == JenisTransaksi.PENGAMBILAN) "Pengambilan (Habis Pakai)" else "Peminjaman")
                    cellStyle = style.sel
                }
                row.createCell(4).apply { setCellValue(t.namaOrang); cellStyle = style.sel }
                row.createCell(5).apply { setCellValue(t.jumlah.toDouble()); cellStyle = style.selAngka }
                row.createCell(6).apply { setCellValue(t.satuan); cellStyle = style.sel }
                row.createCell(7).apply {
                    setCellValue(formatTanggalSel.format(Date(t.tanggalTransaksiMillis)))
                    cellStyle = style.sel
                }
                row.createCell(8).apply {
                    setCellValue(t.tanggalKembaliMillis?.let { formatTanggalSel.format(Date(it)) } ?: "-")
                    cellStyle = style.sel
                }
                row.createCell(9).apply { setCellValue(labelStatus(t.status)); cellStyle = style.sel }
                row.createCell(10).apply { setCellValue(t.catatan ?: "-"); cellStyle = style.sel }
            }
            val baseRowDataTerakhir = baris - 1

            tulisBarisTotal(
                sheet, style,
                baseRowData = baseRowData,
                baseRowDataTerakhir = baseRowDataTerakhir,
                kolomNumerik = KOLOM_NUMERIK_TRANSAKSI,
                kolomLabelTotal = 1,
                jumlahKolom = HEADER_TRANSAKSI.size,
                adaData = daftarTransaksi.isNotEmpty(),
                baris = baris
            )

            aturLebarKolomManual(
                sheet,
                header = HEADER_TRANSAKSI,
                lebarMinimal = intArrayOf(4, 14, 22, 20, 22, 8, 8, 18, 18, 14, 24),
                kolomTeksIndexDanNilai = daftarTransaksi.flatMap { t ->
                    listOf(
                        1 to t.kodeBarang, 2 to t.namaBarang, 4 to t.namaOrang,
                        10 to (t.catatan ?: "-")
                    )
                }
            )

            return simpanKeDownloads(context, workbook, "Laporan_Transaksi_Inventaris")
        } finally {
            workbook.close()
        }
    }

    private fun labelStatus(status: String): String = when (status) {
        "DIPINJAM" -> "Dipinjam"
        "DIKEMBALIKAN" -> "Dikembalikan"
        "HILANG" -> "Hilang"
        "RUSAK" -> "Rusak"
        "DIAMBIL" -> "Diambil (Habis Pakai)"
        else -> status
    }

    // =====================================================================
    // Bagian bersama: judul, baris total, styling, lebar kolom, penyimpanan
    // =====================================================================

    /** Menulis judul dokumen + tanggal cetak + 1 baris kosong. @return baris berikutnya yang kosong. */
    private fun tulisJudul(sheet: XSSFSheet, style: GayaLaporan, judul: String, jumlahKolom: Int): Int {
        var baris = 0
        val rowJudul = sheet.createRow(baris++)
        rowJudul.createCell(0).apply { setCellValue(judul); cellStyle = style.judul }
        sheet.addMergedRegion(CellRangeAddress(0, 0, 0, jumlahKolom - 1))

        val rowTanggal = sheet.createRow(baris++)
        rowTanggal.createCell(0).apply {
            setCellValue("Tanggal Cetak: ${formatTanggalJudul.format(Date())}")
            cellStyle = style.subJudul
        }
        sheet.addMergedRegion(CellRangeAddress(1, 1, 0, jumlahKolom - 1))

        return baris + 1 // +1 baris kosong pemisah
    }

    private fun tulisBarisTotal(
        sheet: XSSFSheet,
        style: GayaLaporan,
        baseRowData: Int,
        baseRowDataTerakhir: Int,
        kolomNumerik: IntArray,
        kolomLabelTotal: Int,
        jumlahKolom: Int,
        adaData: Boolean,
        baris: Int
    ) {
        val rowTotal = sheet.createRow(baris)
        for (k in 0 until jumlahKolom) {
            if (k !in kolomNumerik) {
                rowTotal.createCell(k).apply {
                    setCellValue(if (k == kolomLabelTotal) "TOTAL" else "")
                    cellStyle = style.total
                }
            }
        }

        if (adaData) {
            for (kolom in kolomNumerik) {
                val kolomHuruf = CellReference.convertNumToColString(kolom)
                val rowAwalExcel = baseRowData + 1
                val rowAkhirExcel = baseRowDataTerakhir + 1
                rowTotal.createCell(kolom).apply {
                    cellFormula = "SUM($kolomHuruf$rowAwalExcel:$kolomHuruf$rowAkhirExcel)"
                    cellStyle = style.totalAngka
                }
            }
        } else {
            for (kolom in kolomNumerik) {
                rowTotal.createCell(kolom).apply { setCellValue(0.0); cellStyle = style.totalAngka }
            }
        }
    }

    private class GayaLaporan(
        val judul: XSSFCellStyle,
        val subJudul: XSSFCellStyle,
        val header: XSSFCellStyle,
        val sel: XSSFCellStyle,
        val selAngka: XSSFCellStyle,
        val total: XSSFCellStyle,
        val totalAngka: XSSFCellStyle
    )

    private fun buatSemuaStyle(wb: XSSFWorkbook): GayaLaporan {
        val fontJudul = wb.createFont().apply { bold = true; fontHeightInPoints = 14 }
        val judul = wb.createCellStyle().apply { setFont(fontJudul); alignment = HorizontalAlignment.LEFT }

        val fontSubJudul = wb.createFont().apply { italic = true; fontHeightInPoints = 10 }
        val subJudul = wb.createCellStyle().apply { setFont(fontSubJudul) }

        val fontHeader = wb.createFont().apply { bold = true; color = IndexedColors.WHITE.index }
        val header = wb.createCellStyle().apply {
            setFont(fontHeader)
            fillForegroundColor = IndexedColors.BLUE_GREY.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
            wrapText = true
            terapkanBorderTipis(this)
        }

        val sel = wb.createCellStyle().apply {
            alignment = HorizontalAlignment.LEFT
            verticalAlignment = org.apache.poi.ss.usermodel.VerticalAlignment.CENTER
            terapkanBorderTipis(this)
        }

        val selAngka = wb.createCellStyle().apply {
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = org.apache.poi.ss.usermodel.VerticalAlignment.CENTER
            terapkanBorderTipis(this)
        }

        val fontTotal = wb.createFont().apply { bold = true }
        val total = wb.createCellStyle().apply {
            setFont(fontTotal)
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.LEFT
            terapkanBorderTipis(this)
        }

        val totalAngka = wb.createCellStyle().apply {
            setFont(fontTotal)
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
            terapkanBorderTipis(this)
        }

        return GayaLaporan(judul, subJudul, header, sel, selAngka, total, totalAngka)
    }

    private fun terapkanBorderTipis(style: CellStyle) {
        style.borderTop = BorderStyle.THIN
        style.borderBottom = BorderStyle.THIN
        style.borderLeft = BorderStyle.THIN
        style.borderRight = BorderStyle.THIN
    }

    /**
     * Estimasi lebar kolom manual berdasarkan panjang teks terpanjang di tiap
     * kolom, sebagai pengganti Sheet#autoSizeColumn yang tidak aman di Android.
     * Satuan lebar POI adalah 1/256 karakter, jadi dikalikan 256.
     */
    private fun aturLebarKolomManual(
        sheet: XSSFSheet,
        header: Array<String>,
        lebarMinimal: IntArray,
        kolomTeksIndexDanNilai: List<Pair<Int, String>>
    ) {
        val lebarKarakter = IntArray(header.size) { i -> maxOf(lebarMinimal[i], header[i].length) }

        kolomTeksIndexDanNilai.forEach { (kolom, nilai) ->
            lebarKarakter[kolom] = maxOf(lebarKarakter[kolom], nilai.length)
        }

        lebarKarakter.forEachIndexed { i, lebar ->
            val lebarAkhir = minOf((lebar + 2) * 256, 255 * 256)
            sheet.setColumnWidth(i, lebarAkhir)
        }
    }

    // ---------------- Penyimpanan via MediaStore (Scoped Storage) ----------------

    private fun simpanKeDownloads(context: Context, workbook: XSSFWorkbook, prefixNamaFile: String): Uri? {
        val namaFile = "${prefixNamaFile}_${
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        }.xlsx"

        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ : simpan ke Downloads lewat MediaStore, TANPA permission runtime.
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, namaFile)
                put(MediaStore.Downloads.MIME_TYPE, MIME_XLSX)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null

            resolver.openOutputStream(uri)?.use { out -> workbook.write(out) }
                ?: return null

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            return uri
        } else {
            // Fallback untuk Android < 10. Proyek ini minSdk 26 jadi blok ini
            // jarang terpakai, tapi tetap disediakan untuk jaga-jaga.
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = java.io.File(downloadsDir, namaFile)
            java.io.FileOutputStream(file).use { out -> workbook.write(out) }
            return Uri.fromFile(file)
        }
    }
}
