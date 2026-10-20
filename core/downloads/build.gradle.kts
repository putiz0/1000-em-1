import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.playbox.offline.core.downloads"
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
    api(project(":core:gameapi"))
    implementation(project(":core:database"))
    implementation(project(":core:packs"))

    implementation(libs.okhttp)
    // coroutines: uso direto de Mutex/Semaphore/Flows além do que já
    // entra transitivamente pelas libs do AndroidX.
    implementation(libs.coroutines.core)

    testImplementation(libs.kotlin.test)
}
