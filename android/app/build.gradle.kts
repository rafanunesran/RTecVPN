plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.com.rtec.vpn"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.rtec.vpn"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Biblioteca oficial do WireGuard para Android (backend em Go, roda em espaço de usuário)
    implementation("com.wireguard.android:tunnel:1.0.20230706")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.android.material:material:1.12.0")
    // Leitura de QR code gerado pelo add-client.sh
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
