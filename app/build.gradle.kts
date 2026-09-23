plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.appupdater"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.appupdater"
    minSdk = 24
    targetSdk = 36

    // 版本号支持环境变量覆盖（APP_VERSION_CODE / APP_VERSION_NAME），
    // 便于一键产出 1.0.0 → 1.0.1 升级演示包（配合 scripts/build_apks.sh）。
    versionCode = System.getenv("APP_VERSION_CODE")?.toIntOrNull() ?: 1
    versionName = System.getenv("APP_VERSION_NAME") ?: "1.0.0"

    // 版本更新检查地址（生产环境通过环境变量 UPDATE_CHECK_URL 注入；
    // 留空时 AppUpdateChecker 会回退到内置 assets/update_demo.json 演示配置）
    val updateCheckUrl = (System.getenv("UPDATE_CHECK_URL") ?: "").replace("\"", "\\\"")
    buildConfigField("String", "UPDATE_CHECK_URL", "\"$updateCheckUrl\"")
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.okhttp)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
