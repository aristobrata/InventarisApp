# Aturan default kosong. Karena isMinifyEnabled = false pada build type release
# di proyek contoh ini, file ini belum aktif dipakai. Jika suatu saat Anda
# mengaktifkan minify, tambahkan rule berikut agar Apache POI tidak terpotong:
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }
-keep class org.openxmlformats.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn javax.xml.stream.**
