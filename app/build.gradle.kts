plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.inventaris.peminjaman"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.inventaris.peminjaman"
        // minSdk WAJIB >= 26 (Android 8.0 / Oreo). Apache POI (poi-ooxml) memakai
        // MethodHandle.invoke/invokeExact (invoke-polymorphic) di kelas
        // org.apache.poi.poifs.nio.CleanerUtil, dan fitur bahasa ini baru
        // didukung Dalvik/ART mulai API 26. Menurunkan minSdk di bawah 26 akan
        // menyebabkan error saat proses dexing (D8):
        // "MethodHandle.invoke and MethodHandle.invokeExact are only supported
        //  starting with Android O (--min-api 26)".
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // POI + xmlbeans + commons-compress mendorong jumlah method > 65k pada minSdk lama
        multiDexEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // Diperlukan agar library seperti Apache POI (banyak memakai java.time / try-with-resources versi baru) aman di minSdk 24
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Mengunci toolchain JDK yang dipakai KHUSUS untuk mengompilasi Kotlin,
// terlepas dari JDK sistem/IDE yang sedang aktif (mis. JDK 25 yang belum
// dikenali Kotlin compiler 1.9.x dan menyebabkan error
// "IllegalArgumentException: 25.0.2" saat parsing versi JDK).
// Gradle akan otomatis mencari/menyediakan JDK 17 yang cocok.
kotlin {
    jvmToolchain(17)
}

android {

    buildFeatures {
        viewBinding = true
    }

    packaging {
        // Konflik file META-INF antar poi, xmlbeans, dan commons-compress
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA"
            )
        }
    }
}

dependencies {
    // --- Core / UI ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.fragment:fragment-ktx:1.8.2")

    // --- Lifecycle / ViewModel / Coroutines ---
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // --- Room (SQLite abstraction), 100% offline ---
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // --- Apache POI untuk ekspor .xlsx ---
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5") {
        // xmlbeans & curvesapi ikut terbawa otomatis, biarkan default
    }

    // core library desugaring runtime
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
