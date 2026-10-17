import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Load local.properties for secret defaults (file is gitignored)
val localProperties = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) load(localPropsFile.inputStream())
}

// Resolve a build value: local.properties → CI environment variable → hardcoded fallback.
// This means local dev uses local.properties, CI uses GitHub secrets, neither leaks into git.
fun buildProp(localKey: String, envVar: String, fallback: String): String =
    localProperties.getProperty(localKey)
        ?: System.getenv(envVar)
        ?: fallback

// Allow versionName / versionCode to be overridden from the command line, e.g. in CI:
//   ./gradlew assembleRelease -PversionName=1.2.3 -PversionCode=10203
val ciVersionName: String = project.findProperty("versionName") as String? ?: "1.0"
val ciVersionCode: Int = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.primarysuite.app"
    compileSdk = 36

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("SIGNING_STORE_FILE") ?: ""
            val storePass    = System.getenv("SIGNING_STORE_PASSWORD") ?: ""
            val alias        = System.getenv("SIGNING_KEY_ALIAS") ?: ""
            val keyPass      = System.getenv("SIGNING_KEY_PASSWORD") ?: ""
            if (storeFilePath.isNotEmpty()) {
                storeFile     = file(storeFilePath)
                storePassword = storePass
                keyAlias      = alias
                keyPassword   = keyPass
            }
        }
    }

    defaultConfig {
        applicationId = "com.primarysuite.app"
        minSdk = 23
        targetSdk = 35
        versionCode = ciVersionCode
        versionName = ciVersionName

        // Inject WiFi / WebSocket defaults from local.properties into BuildConfig
        buildConfigField("String", "DEFAULT_WIFI_SSID",     "\"${buildProp("primarysuite.wifi.ssid",     "PRIMARYSUITE_WIFI_SSID",     "")}\"")
        buildConfigField("String", "DEFAULT_WIFI_PASSWORD", "\"${buildProp("primarysuite.wifi.password", "PRIMARYSUITE_WIFI_PASSWORD", "")}\"")
        buildConfigField("String", "DEFAULT_WS_IP",         "\"${buildProp("primarysuite.ws.ip",         "PRIMARYSUITE_WS_IP",         "10.0.0.1")}\"")
        buildConfigField("String", "DEFAULT_WS_PORT",       "\"${buildProp("primarysuite.ws.port",       "PRIMARYSUITE_WS_PORT",       "8080")}\"")
        // Fail the build immediately if SSID is missing — prevents a silent empty-string APK
        require(buildProp("primarysuite.wifi.ssid", "PRIMARYSUITE_WIFI_SSID", "").isNotBlank()) {
            "DEFAULT_WIFI_SSID is blank. Set primarysuite.wifi.ssid in local.properties (local) or PRIMARYSUITE_WIFI_SSID env var (CI)."
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // Allows use of java.time and other Java 8+ APIs on API < 26 via D8/R8 desugaring.
        // Required if minSdk is ever lowered below 26, and is a zero-cost defensive measure otherwise.
        isCoreLibraryDesugaringEnabled = true
    }
    testOptions {
        unitTests {
            // Robolectric needs merged resources and the manifest.
            isIncludeAndroidResources = true
            // android.util.Log and friends return defaults instead of throwing in plain JVM tests.
            isReturnDefaultValues = true
            all { test ->
                // Each Robolectric/Compose test JVM loads the Android framework and uses
                // about 1 GB. WSL has 8 GB shared with the Gradle and Kotlin daemons, so keep
                // the number of workers and their heaps bounded. Override with -PtestForks=N.
                test.maxParallelForks = (project.findProperty("testForks") as String?)?.toIntOrNull() ?: 2
                test.maxHeapSize = "1g"
            }
        }
    }
}

kotlin {
    jvmToolchain(11)
}

// The app compiles for Java 11, but Robolectric needs Java 17+ to run SDK 35 sandboxes.
tasks.withType<Test>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) })
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)
    // Hosts composables in Robolectric tests (createComposeRule).
    debugImplementation(libs.compose.ui.test.manifest)
    implementation(libs.okhttp)
    // Jeopardy image clues, loaded from the Pi over the app's OkHttp.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.androidx.security.crypto)

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // Tests run on the JVM only (no emulator): JUnit, coroutines-test, Turbine,
    // Robolectric for Android framework classes, MockWebServer for sockets.
    testImplementation(libs.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    // Real org.json for plain JVM tests (android.jar only has stubs).
    testImplementation(libs.org.json)
}
