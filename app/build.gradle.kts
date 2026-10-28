plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Fonction pour récupérer le nom de la version via Git (ex: "v1.2" ou le dernier tag)
fun getGitVersionName(): String {
    return try {
        providers.exec {
            commandLine("git", "describe", "--tags", "--always")
        }.standardOutput.asText.get().trim().ifEmpty { "1.2" }
    } catch (_: Exception) {
        "1.2"
    }
}

// Fonction pour générer un versionCode incrémentiel basé sur le nombre de commits Git
fun getGitVersionCode(): Int {
    return try {
        val count = providers.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
        }.standardOutput.asText.get().trim()
        count.toIntOrNull() ?: 1
    } catch (_: Exception) {
        1
    }
}

android {
    namespace = "fr.anthspl.etatpontlehavre"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.anthspl.etatpontlehavre"
        minSdk = 26
        targetSdk = 36

        // --- UTILISATION AUTOMATIQUE GIT ---
        versionCode = getGitVersionCode() // S'incrémente à chaque commit
        versionName = getGitVersionName() // Prend le tag Git (ex: v1.2) ou l'ID du commit

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation("org.jsoup:jsoup:1.17.2")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // OpenStreetMap pour Android (osmdroid)
    implementation("org.osmdroid:osmdroid-android:6.1.18")
}