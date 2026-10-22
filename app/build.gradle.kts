import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Signature release : keystore.properties à la racine (ignoré par git), sinon variables d'environnement
 * RACKOWL_STORE_FILE, RACKOWL_STORE_PASSWORD, RACKOWL_KEY_ALIAS, RACKOWL_KEY_PASSWORD (CI).
 * Sans l'un ni l'autre, la variante release est produite non signée.
 */
val signing: Map<String, String>? = run {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        Properties().apply { file.inputStream().use { load(it) } }.let { p -> p.stringPropertyNames().associateWith { p.getProperty(it) } }
    } else {
        System.getenv("RACKOWL_STORE_FILE")?.let {
            mapOf(
                "storeFile" to it,
                "storePassword" to System.getenv("RACKOWL_STORE_PASSWORD").orEmpty(),
                "keyAlias" to System.getenv("RACKOWL_KEY_ALIAS").orEmpty(),
                "keyPassword" to System.getenv("RACKOWL_KEY_PASSWORD").orEmpty(),
            )
        }
    }
}

android {
    namespace = "com.rackowl"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rackowl"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0"
    }

    signingConfigs {
        if (signing != null) create("release") {
            storeFile = file(signing.getValue("storeFile"))
            storePassword = signing.getValue("storePassword")
            keyAlias = signing.getValue("keyAlias")
            keyPassword = signing.getValue("keyPassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
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

    // APK nommé Rackowl-v<version>-<variante>.apk
    base.archivesName.set("Rackowl-v${defaultConfig.versionName}")

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(libs.okhttp.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.snakeyaml.engine)
    implementation(libs.androidx.biometric)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
