buildscript {
    dependencies {
        // AGP's built-in Kotlin compiles with whichever Kotlin Gradle plugin is on this classpath.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.paparazzi) apply false
}
