import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// Shared Kotlin Multiplatform module.
// commonMain runs on BOTH Android and iOS; androidMain / iosMain hold the thin
// platform-specific actuals. iosArm64()/iosSimulatorArm64() are added when building
// on a Mac. See /opt/automateLinux/.claude/docs/kmp-migration-playbook.md
kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    // iosArm64(); iosSimulatorArm64()   // enable on a Mac

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            // Explicit, not transitive: `implementation` hides a dependency's own
            // dependencies from the compile classpath, so Dispatchers/withContext
            // in androidMain would not resolve through compose.runtime.
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.multiplatform.settings)
        }
    }
}

android {
    namespace = "com.automatelinux.carCheck.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
