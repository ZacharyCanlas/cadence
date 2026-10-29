plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.paparazzi)
}

val signingProperties = listOf(
    "CADENCE_KEYSTORE",
    "CADENCE_KEYSTORE_PASSWORD",
    "CADENCE_KEY_ALIAS",
    "CADENCE_KEY_PASSWORD",
).associateWith { providers.gradleProperty(it).orNull?.takeIf(String::isNotBlank) }

android {
    namespace = "io.github.zacharycanlas.cadence"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.zacharycanlas.cadence"
        minSdk = 33
        targetSdk = 36
        // Android refuses an APK whose versionCode is not above the installed copy's,
        // so every APK that leaves this machine bumps it, committed first.
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            signingProperties["CADENCE_KEYSTORE"]?.let { storeFile = file(it) }
            storePassword = signingProperties["CADENCE_KEYSTORE_PASSWORD"]
            keyAlias = signingProperties["CADENCE_KEY_ALIAS"]
            keyPassword = signingProperties["CADENCE_KEY_PASSWORD"]
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

// Runs when a release build executes, not at configuration, so debug builds,
// tests and lint (and CI) need none of the signing properties.
val checkReleaseSigning = tasks.register("checkReleaseSigning") {
    val missing = signingProperties.filterValues { it == null }.keys.toList()
    doLast {
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Release signing needs ${missing.joinToString()} in ~/.gradle/gradle.properties.",
            )
        }
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild") dependsOn(checkReleaseSigning)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)

    testImplementation(libs.junit)
}
