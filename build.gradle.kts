plugins {
    // version aliases declared in gradle/libs.versions.toml, applied in subprojects
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.compilerCompose) apply false
}
