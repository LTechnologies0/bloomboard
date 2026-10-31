plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val releaseBuild = gradle.startParameter.taskNames.any { task ->
    task.contains("Release", ignoreCase = true) || task.contains("bundle", ignoreCase = true)
}

android {
    namespace = "dev.ltechnologies.bloomboard"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "dev.ltechnologies.bloomboard"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if (!releaseBuild) {
            ndk {
                abiFilters += listOf("arm64-v8a", "x86_64")
            }
        }
    }

    splits {
        abi {
            isEnable = releaseBuild
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    signingConfigs {
        create("release") {
            val store = System.getenv("BLOOMBOARD_KEYSTORE")
                ?: project.findProperty("BLOOMBOARD_KEYSTORE")?.toString()
            if (!store.isNullOrBlank()) {
                storeFile = file(store)
                storePassword = System.getenv("BLOOMBOARD_STORE_PASSWORD")
                    ?: project.findProperty("BLOOMBOARD_STORE_PASSWORD")?.toString()
                keyAlias = System.getenv("BLOOMBOARD_KEY_ALIAS")
                    ?: project.findProperty("BLOOMBOARD_KEY_ALIAS")?.toString()
                keyPassword = System.getenv("BLOOMBOARD_KEY_PASSWORD")
                    ?: project.findProperty("BLOOMBOARD_KEY_PASSWORD")?.toString()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseStore = signingConfigs.getByName("release").storeFile
            val requireReleaseSigning = System.getenv("BLOOMBOARD_REQUIRE_SIGNING") == "true"
            signingConfig = when {
                releaseStore?.exists() == true -> signingConfigs.getByName("release")
                requireReleaseSigning -> throw GradleException(
                    "Release signing requires GitHub secrets BLOOMBOARD_KEYSTORE_BASE64, BLOOMBOARD_STORE_PASSWORD, BLOOMBOARD_KEY_ALIAS and BLOOMBOARD_KEY_PASSWORD.",
                )
                else -> signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

base {
    archivesName.set("Bloomboard-${android.defaultConfig.versionName}")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
