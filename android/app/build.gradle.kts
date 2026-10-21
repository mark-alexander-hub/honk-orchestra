import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing: a keystore.properties OUTSIDE the repo, found via HONK_KEYSTORE_PROPS
// or %LOCALAPPDATA%\honk-orchestra-keys\keystore.properties. Without it the release build is unsigned.
val keystoreProps = Properties().apply {
    listOfNotNull(
        System.getenv("HONK_KEYSTORE_PROPS"),
        System.getenv("LOCALAPPDATA")?.let { "$it/honk-orchestra-keys/keystore.properties" }
    ).map { file(it) }.firstOrNull { it.exists() }?.inputStream()?.use { load(it) }
}

// The app ships the same index.html as the website, copied in at build time so there is one source.
val copyWeb by tasks.registering(Copy::class) {
    from(rootProject.file("../index.html"))
    into(layout.buildDirectory.dir("web"))
}
tasks.named("preBuild") { dependsOn(copyWeb) }

android {
    namespace = "io.github.markalexanderhub.honkorchestra"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.markalexanderhub.honkorchestra"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    sourceSets {
        getByName("main") { assets.srcDir(layout.buildDirectory.dir("web")) }
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
    // Newer versions of these (core 1.19, material 1.14 as of Oct 2026)
    // need compileSdk 37 and Android Gradle plugin 9.1. Bump them together when the toolchain moves.
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.webkit:webkit:1.12.1")


}
