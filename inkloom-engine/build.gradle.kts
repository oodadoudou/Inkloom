// Inkloom's drawing engine: tile surfaces, paint ops, stroke runtime, brush presets, brush importers
// (.sut, .abr, .brush/.brushset) and the native raster library. Inkloom's app builds on it, and other
// apps can use it through a composite build: includeBuild("<Inkloom checkout>") and
// implementation("app.inkloom:inkloom-engine").
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

group = "app.inkloom"

android {
    namespace = "app.inkloom.engine"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
        // Each app keeps only the ABIs it ships: Inkloom packages arm64, the Dodo apps add x86_64.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-Wall", "-Wextra", "-Werror")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    ndkVersion = "27.2.12479018"
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    testImplementation("junit:junit:4.13.2")
}
