plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
dependencies { implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0") }
android { namespace = "com.magic76.karaokelab"; compileSdk = 35
 defaultConfig { applicationId = "com.magic76.karaokelab"; minSdk = 29; targetSdk = 35; versionCode = 7; versionName = "0.7" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}