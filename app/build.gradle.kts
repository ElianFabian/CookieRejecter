import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasKeystore = keystorePropertiesFile.exists()
if (hasKeystore) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "dev.macbuildssys.consentguard"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.cookierejecter"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "0.0.3"
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/kotlin")
        }
    }

    /* Needed for BuildConfig.DEBUG, used to compile the verbose step by
       step logging out of release builds entirely while keeping it for
       debug builds. AGP 8 stopped generating BuildConfig by default. */
    buildFeatures {
        buildConfig = true
    }

    /* Keeps Google's signed dependency list out of the APK. It is not needed
       here, and leaving it out is what lets F-Droid and anyone else rebuild
       the APK and get the same bytes. */
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    if (hasKeystore) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
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
