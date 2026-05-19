plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.example.nonogram"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.sorapenguin.nonogram"
        minSdk = 26
        targetSdk = 34
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("String", "API_BASE_URL", "\"https://game.sorapenguin.dev/nonogram/\"")
    }

    // ============================================================
    // 署名設定
    // 環境変数またはローカルの keystore/ ディレクトリを参照する
    // 詳細: PLAYSTORE_RELEASE.md > Step 1
    // ============================================================
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "keystore/nonogram.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: "nonogram"
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    // ============================================================
    // 4 バリアント構成
    //
    //  flavor \ buildType |  debug                          | release
    // --------------------+---------------------------------+----------------------------------
    //  store              | storeDebug                      | storeRelease  ← Play Store
    //                     | ADS=true / FAKE=false           | ADS=true / FAKE=false
    //                     | テスト AdMob ID / ローカルAPI   | 本番 AdMob ID (要差替) / 本番API
    // --------------------+---------------------------------+----------------------------------
    //  portfolio          | portfolioDebug                  | portfolioRelease  ← GitHub配布
    //                     | ADS=false / FAKE=true           | ADS=false / FAKE=true
    //                     | 偽広告3秒 / ローカルAPI         | 偽広告3秒 / 本番API
    // ============================================================
    flavorDimensions += "distribution"
    productFlavors {
        create("store") {
            dimension = "distribution"
            buildConfigField("Boolean", "ADS_ENABLED", "true")
            buildConfigField("Boolean", "FAKE_AD_MODE", "false")

            // AdMob ID（テスト用）
            // TODO [storeRelease]: 下記 3 箇所を本番 ID に差し替える → PLAYSTORE_RELEASE.md > Step 2
            buildConfigField("String", "ADMOB_APP_ID",      "\"ca-app-pub-3940256099942544~3347511713\"")
            buildConfigField("String", "ADMOB_REWARDED_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
        }
        create("portfolio") {
            dimension = "distribution"
            buildConfigField("Boolean", "ADS_ENABLED", "false")
            buildConfigField("Boolean", "FAKE_AD_MODE", "true")
            // portfolio では広告を使わないが、BuildConfig の型を合わせるため同じ値を入れる
            buildConfigField("String", "ADMOB_APP_ID",      "\"ca-app-pub-3940256099942544~3347511713\"")
            buildConfigField("String", "ADMOB_REWARDED_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            applicationIdSuffix = ".portfolio"
            versionNameSuffix = "-portfolio"
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8082/nonogram/\"")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Preferences DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // AdMob
    implementation("com.google.android.gms:play-services-ads:23.0.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.9")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
