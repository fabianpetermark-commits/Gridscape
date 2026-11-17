plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.minigames.gridscape"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.minigames.gridscape"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:models"))
    implementation(project(":core:engine"))
    implementation(project(":core:input"))
    implementation(project(":core:rendering"))
    implementation(project(":core:storage"))
    implementation(project(":games:grid-hunter"))

    // Isolated libGDX prototype host; Compose remains the production launcher.
    implementation("com.badlogicgames.gdx:gdx:1.13.1")
    implementation("com.badlogicgames.gdx:gdx-backend-android:1.13.1")
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:1.13.1:natives-arm64-v8a")
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:1.13.1:natives-armeabi-v7a")
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:1.13.1:natives-x86")
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:1.13.1:natives-x86_64")

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
