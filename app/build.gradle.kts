import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.timeapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.tafsirnahian5.time"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing (Phase 16) reads an untracked keystore.properties at the project root:
    // storeFile, storePassword, keyAlias, keyPassword. Without it the release build is unsigned
    // (still buildable), and no key or password ever lives in the repository.
    val keystoreProps = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f ->
        Properties().apply { f.inputStream().use { load(it) } }
    }
    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Shrink and optimise, but never rename: enum names are stored in the database and in
            // backups (see proguard-rules.pro), so obfuscation would break existing data.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProps != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // F-Droid can't read the encrypted dependency list Google Play uses, and rejects APKs that carry it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // The exported schema JSONs are what MigrationTest builds each old version from.
    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    // Automatic backups (Phase 17): the one background-work library, for a periodic job that
    // survives reboots and respects battery.
    implementation(libs.androidx.work.runtime.ktx)
    // Forces a modern androidx.fragment resolution — biometric:1.1.0 transitively pulls
    // fragment:1.2.5, whose FragmentActivity.checkForValidRequestCode 16-bit limit crashes
    // any ActivityResultContracts launch (e.g. Tier 9's SAF CreateDocument/OpenDocument)
    // against the modern activity:1.13.0 ActivityResultRegistry's wider request codes.
    // Fixed upstream in fragment 1.5.5+; pinned well past that.
    implementation(libs.androidx.fragment)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    // room-testing reads schema JSON with kotlinx-serialization 1.8; navigation holds the app at
    // 1.7.3, and the test APK is forced onto the app's versions, so MigrationTest crashed with
    // AbstractMethodError. Raised for debug (what the tests run against) only — release unchanged.
    debugImplementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}