plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.workwatch"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.workwatch"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Added Gson for JSON serialization
    implementation("com.google.code.gson:gson:2.10.1")

    // Android WorkManager for background tasks
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Google Play Services Location
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Google Play Integrity API
    implementation("com.google.android.play:integrity:1.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.1")

    // NanoHTTPD for P2P server
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Room Database for persistence (KSP'ye güncellendi)
    implementation("androidx.room:room-runtime:2.6.1") // NOT: 2.6.1 genellikle daha stabil
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1") // <-- DEĞİŞİKLİK 1

    // Hilt for Dependency Injection (KSP'ye güncellendi ve düzeltildi)
    implementation("com.google.dagger:hilt-android:2.48")
    ksp("com.google.dagger:hilt-compiler:2.48") // <-- DEĞİŞİKLİK 2
    implementation("androidx.hilt:hilt-work:1.1.0")
    ksp("androidx.hilt:hilt-compiler:1.1.0") // <-- DEĞİŞİKLİK 3

    // OkHttp (Telegram bot)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Firebase BOM - manages all Firebase library versions
    implementation(platform("com.google.firebase:firebase-bom:32.7.1"))

    // Firebase services
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
}
