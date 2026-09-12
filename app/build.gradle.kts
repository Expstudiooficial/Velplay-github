plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.expstudio.facilitycore"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.expstudio.facilitycore"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "2.0.0"
    }

    /**
     * One fixed key for every build, checked into the repo.
     *
     * Android refuses to install an update whose signature differs from the
     * installed app, and the auto-generated debug keystore is created per
     * machine — so every build from a different machine or CI run produced an
     * APK that could only be installed by uninstalling the game first, losing
     * every save. Signing with a key that lives in the repo makes any build
     * update any earlier one.
     *
     * This key is therefore public. It is fine for sideloading a hobby game and
     * would NOT be fine for a Play Store listing: publishing there needs a
     * private upload key kept out of version control.
     */
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("keystore/facility-core.jks")
            storePassword = "facilitycore"
            keyAlias = "facilitycore"
            keyPassword = "facilitycore"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    testImplementation("junit:junit:4.13.2")
    // Renders real frames off-device so the look can be checked, not guessed.
    testImplementation("org.robolectric:robolectric:4.13")
}
