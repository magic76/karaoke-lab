plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android { namespace = "com.magic76.karaokelab"; compileSdk = 35
 defaultConfig { applicationId = "com.magic76.karaokelab"; minSdk = 29; targetSdk = 35; versionCode = 21; versionName = "0.21" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}

dependencies { implementation("com.microsoft.onnxruntime:onnxruntime-android:1.31.1"); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0") }
