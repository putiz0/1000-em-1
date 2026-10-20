import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.playbox.offline.core.catalog"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // O tipo InstallStatus (estados do jogo) pertence ao banco e é exposto via catálogo.
    api(project(":core:database"))
    // A fonte remota (interface RemoteCatalogSource) é trocada pelo :core:downloads.
    api(project(":core:gameapi"))
    implementation(libs.serialization.json)

    testImplementation(libs.kotlin.test)
}
