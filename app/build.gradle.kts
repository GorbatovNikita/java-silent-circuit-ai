plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.chaquopy)
}

android {
    namespace = "ru.hck.voice"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "ru.hck.voice"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
            }
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            externalNativeBuild {
                cmake {
                    arguments += listOf(
                        "-DCMAKE_BUILD_TYPE=Release"
                    )
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

chaquopy {
    defaultConfig {
        version = "3.14"
//        buildPython("путь/к/python3.10")
        pip {
            options("--find-links", rootProject.file("app/wheels").absolutePath)
            install("numpy==2.6.0.dev0")
            install("sherpa-onnx==2.0.1")

        }
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    implementation("androidx.room:room-runtime:2.8.4")
    implementation(files("libs/onnxruntime-classes.jar"))
    annotationProcessor("androidx.room:room-compiler:2.8.4")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}