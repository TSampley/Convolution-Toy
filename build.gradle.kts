// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinAndroid) apply false

    alias(libs.plugins.kotlinxSerialization) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinCocoapods) apply false
    alias(libs.plugins.composePlugin) apply false
    alias(libs.plugins.composeCompiler) apply false

    // apply dokka now
    alias(libs.plugins.dokka)

    alias(libs.plugins.sqlDelight) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.publishMultiplatform) apply false

    alias(libs.plugins.kotlinxKover)
}
