plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose"); id("com.google.devtools.ksp") }
android {
 namespace = "id.local.tubeflow"
 compileSdk = 37
 defaultConfig { applicationId = "id.local.tubeflow"; minSdk = 29; targetSdk = 37; versionCode = 5; versionName = "1.0.4" }
 splits { abi { isEnable = true; reset(); include("arm64-v8a", "armeabi-v7a", "x86_64"); isUniversalApk = false } }
 buildFeatures { compose = true; buildConfig = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 packaging { jniLibs { useLegacyPackaging = true; keepDebugSymbols += "**/*.zip.so" } }
 val signingValues = listOf("TUBEFLOW_KEYSTORE_PATH", "TUBEFLOW_KEYSTORE_PASSWORD", "TUBEFLOW_KEY_ALIAS", "TUBEFLOW_KEY_PASSWORD").map { System.getenv(it) }
 if (signingValues.all { !it.isNullOrBlank() }) { signingConfigs { create("production") { storeFile = file(signingValues[0]!!); storePassword = signingValues[1]; keyAlias = signingValues[2]; keyPassword = signingValues[3] } } }
 buildTypes { release { if (signingValues.all { !it.isNullOrBlank() }) signingConfig = signingConfigs.getByName("production"); isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
 implementation(platform("androidx.compose:compose-bom:2026.09.00"))
 implementation("androidx.activity:activity-compose:1.13.0")
 implementation("androidx.core:core-splashscreen:1.2.0")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.compose.ui:ui-tooling-preview")
 debugImplementation("androidx.compose.ui:ui-tooling")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
 implementation("androidx.work:work-runtime-ktx:2.12.0")
 implementation("androidx.room:room-runtime:2.8.5")
 implementation("androidx.room:room-ktx:2.8.5")
 ksp("androidx.room:room-compiler:2.8.5")
 implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
 implementation(files("libs/ffmpeg-0.18.1-webp16k.aar"))
 implementation("io.coil-kt:coil-compose:2.7.0")
 testImplementation("junit:junit:4.13.2")
 // org.json asli untuk unit test: stub android.jar melempar "not mocked" — Lyrics.fetch diuji dengan fixture HTTP (BUG-013/014).
 testImplementation("org.json:json:20260814")
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }
