import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// Chaves de assinatura locais (armazenadas em local.properties, fora do repositório).
// OBS: para publicar na Play Store, crie um keystore definitivo e NÃO reutilize
// este arquivo de desenvolvimento. O segredo fica somente na sua máquina.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.playbox.offline"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.playbox.offline"
        minSdk = 24
        targetSdk = 36
        versionCode = 10
        versionName = "0.3.3"

        // Servidor de packs. Durante o desenvolvimento é o servidor do PC
        // (via adb reverse: "adb reverse tcp:9080 tcp:9080" liga o localhost
        // do celular ao PC). Na Fase 5 passa a ser a URL dos GitHub Releases.
        buildConfigField("String", "PACK_BASE_URL", "\"http://localhost:9080\"")
    }

    signingConfigs {
        if (keystoreProperties.getProperty("playbox.storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("playbox.storeFile"))
                storePassword = keystoreProperties.getProperty("playbox.storePassword")
                keyAlias = keystoreProperties.getProperty("playbox.keyAlias")
                keyPassword = keystoreProperties.getProperty("playbox.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // O lint estrito é revisado na Fase 6 (preparação para a Play Store).
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:catalog"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:gameapi"))
    implementation(project(":core:packs"))
    implementation(project(":core:downloads"))
    implementation(project(":games:damas"))
    implementation(project(":games:xadrez"))
    implementation(project(":games:domino"))
    implementation(project(":games:imperio"))
    implementation(project(":games:sushi"))

    // OkHttp usado diretamente pelo AppContainer (client compartilhado)
    implementation(libs.okhttp)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)

    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.datastore.preferences)
}
