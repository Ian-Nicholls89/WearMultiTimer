import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Release signing comes from, in order: the WMT_* environment variables (set by the release
 * workflow from the repo's secrets), then a local keystore.properties. Without either, a local
 * release build falls back to the debug key - but the release workflow passes
 * -PrequireReleaseKey so a release can never go out signed with the wrong key.
 */
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)

val releaseStoreFile = signingValue("WMT_KEYSTORE_FILE", "storeFile")
    ?.let { rootProject.file(it) }?.takeIf { it.exists() }

if (releaseStoreFile == null && providers.gradleProperty("requireReleaseKey").isPresent) {
    throw GradleException(
        "No release keystore: set WMT_KEYSTORE_FILE (and its passwords) or keystore.properties."
    )
}

android {
    namespace = "io.github.iannicholls89.wearmultitimer"
    compileSdk = 37

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = signingValue("WMT_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("WMT_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("WMT_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "io.github.iannicholls89.wearmultitimer"
        // Wear OS 3, the oldest the Pixel Watch line has run.
        minSdk = 30
        targetSdk = 37
        versionCode = providers.gradleProperty("appVersionCode").get().toInt()
        versionName = providers.gradleProperty("appVersionName").get()
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.navigation)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // The timers and presets, saved as one JSON file.
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    // Screenshots of the watch screens on the JVM, run with -Pscreenshots only.
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.withType<Test>().configureEach {
    if (!project.hasProperty("screenshots")) exclude("**/screenshots/**")
    else systemProperty("roborazzi.test.record", "true")
}
