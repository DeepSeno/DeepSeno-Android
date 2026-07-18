import java.util.Base64
import java.util.Properties

// ── Load secrets from .env file (primary) or local.properties (fallback) ──
// Priority: system env vars > .env > local.properties

val envFile = rootProject.file(".env")
val localPropsFile = rootProject.file("local.properties")

// Parse .env file (KEY=VALUE format, # comments, no quotes)
val envVars = mutableMapOf<String, String>()
if (envFile.exists()) {
    envFile.readLines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx > 0) {
                envVars[trimmed.substring(0, eqIdx).trim()] = trimmed.substring(eqIdx + 1).trim()
            }
        }
    }
}

val localProps = Properties()
if (localPropsFile.exists()) {
    localProps.load(localPropsFile.inputStream())
}

fun getSecret(key: String): String? {
    return System.getenv(key) ?: envVars[key] ?: localProps.getProperty(key)
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.enmooy.deepseno"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.enmooy.deepseno"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "1.6.6"
        // Relay server URL from .env
        buildConfigField("String", "RELAY_SERVER_BASE_URL",
            "\"${getSecret("RELAY_SERVER_BASE_URL") ?: ""}\"")
    }

    signingConfigs {
        create("release") {
            // Decode keystore from base64 env var if available
            val keystoreBase64 = getSecret("SIGNING_KEYSTORE_BASE64")
            if (!keystoreBase64.isNullOrBlank()) {
                val decodedFile = file("${rootProject.projectDir}/deepseno-release.jks")
                if (!decodedFile.exists()) {
                    decodedFile.writeBytes(Base64.getDecoder().decode(keystoreBase64))
                }
                storeFile = decodedFile
            } else {
                storeFile = file("../deepseno-release.jks")
            }
            storePassword = getSecret("SIGNING_STORE_PASSWORD") ?: ""
            keyAlias = getSecret("SIGNING_KEY_ALIAS") ?: ""
            keyPassword = getSecret("SIGNING_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val versionName = variant.versionName
            output.outputFileName = "deepseno-v${versionName}.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.retrofit.serialization)
    implementation(libs.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)
    implementation(libs.webrtc)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.datastore)
}
