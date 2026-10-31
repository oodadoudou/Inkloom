import java.util.Properties
import org.gradle.api.tasks.testing.Test

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "app.inkloom"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.inkloom"
        minSdk = 29
        targetSdk = 35
        versionCode = 251
        versionName = "0.251.0"
        testInstrumentationRunner = "app.inkloom.InkloomTestRunner"
        buildConfigField("boolean", "PAINTING_DIAGNOSTICS", "false")
        vectorDrawables.useSupportLibrary = true
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++20", "-Wall", "-Wextra", "-Werror")
            }
        }
    }

    // Release signing comes from an ignored keystore.properties (storeFile, storePassword, keyAlias,
    // keyPassword). Without it, local release builds use the debug key so they still install.
    val releaseKeystore = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { file ->
        Properties().apply { file.inputStream().use(::load) }
    }
    signingConfigs {
        if (releaseKeystore != null) create("release") {
            storeFile = rootProject.file(releaseKeystore.getProperty("storeFile"))
            storePassword = releaseKeystore.getProperty("storePassword")
            keyAlias = releaseKeystore.getProperty("keyAlias")
            keyPassword = releaseKeystore.getProperty("keyPassword")
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("boolean", "PAINTING_DIAGNOSTICS", "true")
        }
        getByName("release") {
            buildConfigField("boolean", "PAINTING_DIAGNOSTICS", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        create("profile") {
            initWith(getByName("release"))
            applicationIdSuffix = ".profile"
            versionNameSuffix = "-profile"
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += listOf("release", "debug")
            buildConfigField("boolean", "PAINTING_DIAGNOSTICS", "true")
        }
    }

    // -PinkloomReleaseTests runs the instrumented tests against the shrunk release build, with the
    // app's own classes kept for the tests; this checks the libraries' shrinking rules.
    if (providers.gradleProperty("inkloomReleaseTests").isPresent) {
        testBuildType = "release"
        buildTypes.getByName("release").apply {
            proguardFiles("proguard-release-tests.pro")
            testProguardFiles("proguard-release-tests.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"

    ndkVersion = "27.2.12479018"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

}

// Release builds ship for tablets only; the x86_64 libraries exist for the emulator.
// -PinkloomEmulatorRelease keeps them, to smoke-test a release build on the emulator.
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        if (!providers.gradleProperty("inkloomEmulatorRelease").isPresent) variant.packaging.jniLibs.excludes.add("lib/x86_64/**")
    }
}

tasks.withType<Test>().configureEach {
    providers.gradleProperty("brushSamplesDir").orNull?.let {
        systemProperty("brush.samples.dir", it)
    }
    providers.gradleProperty("procreateSamplesDir").orNull?.let {
        systemProperty("procreate.samples.dir", it)
    }
    providers.gradleProperty("inkloomAuditOutputDir").orNull?.let {
        systemProperty("inkloom.audit.output.dir", it)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.graphics:graphics-core:1.0.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    // ED-10 super resolution: ONNX Runtime (MIT) runs the bundled Real-ESRGAN model (BSD 3-Clause) on the device.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.31.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    // Same version Compose UI test already brings in; declared for the custom InkloomTestRunner.
    // Keep it equal to the resolved transitive version rather than moving the test runtime.
    //noinspection GradleDependency
    androidTestImplementation("androidx.test:runner:1.5.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
