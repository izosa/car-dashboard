import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Personal defaults (servers, home point, ...) stay out of git: copy
// defaults.properties.example to defaults.properties. Missing keys build as empty.
val defaults = Properties().apply {
    rootProject.file("defaults.properties").takeIf { it.exists() }?.reader()?.use { load(it) }
}
fun default(key: String): String = defaults.getProperty(key, "").trim()

android {
    namespace = "com.izosa.home.car.dashboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.izosa.home.car.dashboard"
        minSdk = 26
        targetSdk = 28
        versionCode = 11
        versionName = "1.3.2"

        fun field(name: String, value: String) =
            buildConfigField("String", name, "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
        field("DEFAULT_TRACKING_SERVER", default("tracking.server"))
        field("DEFAULT_TRACKING_DEVICE_ID", default("tracking.deviceId"))
        val home = default("home").split(",").map { it.trim() }
        field("DEFAULT_HOME_LAT", home.getOrElse(0) { "" })
        field("DEFAULT_HOME_LON", home.getOrElse(1) { "" })
        field("SETTINGS_PIN_SHA256", default("settings.pinSha256"))
        field("SHORTCUTS", default("shortcuts").ifEmpty { "front,radio,spotify,traccar,waze" })
        field("PARKING_PACKAGE", default("parking.package"))
        buildConfigField("boolean", "PARKING_ON_ARRIVAL", default("parking.onArrival").toBoolean().toString())
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        disable += "ExpiredTargetSdkVersion"
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
