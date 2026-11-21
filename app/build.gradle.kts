plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.magic76.karaokelab"; compileSdk = 35
 defaultConfig { applicationId = "com.magic76.karaokelab"; minSdk = 29; targetSdk = 35; versionCode = 1; versionName = "0.1" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}