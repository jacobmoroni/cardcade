plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.cardcade.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cardcade.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.ui.tooling)
}

// Auto-send the debug APK to Telegram after assembleDebug. Skipped silently
// when the credentials file at ~/.config/bcnav/phone_agent.env is missing,
// and never fails the build even if the send errors out.
val sendDebugApkToTelegram = tasks.register<Exec>("sendDebugApkToTelegram") {
    group = "distribution"
    description = "Sends app-debug.apk to Telegram via scripts/send_apk_to_telegram.sh."

    val script = rootProject.file("scripts/send_apk_to_telegram.sh")
    val envFile = file("${System.getProperty("user.home")}/.config/bcnav/phone_agent.env")

    onlyIf {
        val ok = script.exists() && envFile.exists()
        if (!ok) logger.lifecycle("sendDebugApkToTelegram: skipped (missing script or ~/.config/bcnav/phone_agent.env).")
        ok
    }

    workingDir = rootProject.projectDir
    commandLine("bash", script.absolutePath)
    isIgnoreExitValue = true
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(sendDebugApkToTelegram)
}
