import korlibs.korge.gradle.*

plugins {
    id("com.soywiz.korge") version "6.0.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20"
}

korge {
    id = "com.example.islanddevkot"
    name = "IslandDevKot"
    description = "無人島開拓 × 放置ゲーム (Korge/Kotlin移植)"
    version = "0.1.0"
    entryPoint = "islanddev.main"
    androidCompileSdk = 34
    androidTargetSdk = 34
    orientation = Orientation.PORTRAIT
    fullscreen = false
    displayCutout = DisplayCutout.NEVER
    targetJvm()
    targetAndroid()
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation("androidx.datastore:datastore-preferences:1.1.1")
        }
    }
}

// kotlin-stdlib-common は Kotlin 2.0 で stdlib に統合済み
configurations.configureEach {
    if (state == org.gradle.api.artifacts.Configuration.State.UNRESOLVED) {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-stdlib-common") {
                useTarget("org.jetbrains.kotlin:kotlin-stdlib:${requested.version}")
            }
        }
    }
}

tasks.configureEach {
    if (name.contains("lint", ignoreCase = true)) {
        setDependsOn(emptyList<Any>())
        enabled = false
    }
}

tasks.named("check") {
    setDependsOn(listOf("jvmTest"))
}

tasks.named("build") {
    setDependsOn(listOf("jvmTest", "assembleDebug"))
}

// Korge 6.0.0 generates a fullscreen/translucent Android theme even when
// fullscreen=false. Replace only that generated theme so Android lays out the
// game inside the visible status/navigation bar area.
tasks.named("createAndroidManifest") {
    doLast {
        val stylesFile = layout.buildDirectory
            .file("platforms/android/androires/values/styles.xml")
            .get()
            .asFile
        val activityFile = layout.buildDirectory
            .file("platforms/android/androisrc/MainActivity.kt")
            .get()
            .asFile

        stylesFile.parentFile.mkdirs()
        stylesFile.writeText(
            """
            <?xml version="1.0" encoding="utf-8" ?>
            <resources>
                <style name="AppThemeOverride" parent="@android:style/Theme.Material.Light.NoActionBar">
                    <item name="android:windowNoTitle">true</item>
                    <item name="android:windowFullscreen">false</item>
                    <item name="android:windowActionModeOverlay">true</item>
                    <item name="android:windowContentOverlay">@null</item>
                    <item name="android:windowLayoutInDisplayCutoutMode">never</item>
                    <item name="android:windowTranslucentStatus">false</item>
                    <item name="android:windowTranslucentNavigation">false</item>
                    <item name="android:windowDrawsSystemBarBackgrounds">true</item>
                    <item name="android:statusBarColor">#000000</item>
                    <item name="android:navigationBarColor">#000000</item>
                    <item name="android:windowLightStatusBar">false</item>
                    <item name="android:windowLightNavigationBar">false</item>
                </style>
            </resources>
            """.trimIndent()
        )

        // KorgwActivity can update system UI flags again during lifecycle and
        // focus changes. Restore normal Android system bars at runtime too.
        activityFile.parentFile.mkdirs()
        activityFile.writeText(
            """
            package com.example.islanddevkot

            import android.graphics.Color
            import android.os.Build
            import android.os.Bundle
            import android.view.View
            import android.view.WindowInsets
            import android.view.WindowManager
            import islanddev.IslandSession
            import islanddev.game.SaveManager
            import islanddev.model.SaveData
            import islanddev.main
            import korlibs.render.GameWindowCreationConfig
            import korlibs.render.KorgwActivity
            import kotlinx.coroutines.runBlocking

            class MainActivity : KorgwActivity(
                config = GameWindowCreationConfig(msaa = 1, fullscreen = false)
            ) {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    installSystemBarGuard()
                    showSystemBars()
                }

                override fun onResume() {
                    super.onResume()
                    window.decorView.post { showSystemBars() }
                }

                override fun onPause() {
                    persistGame()
                    super.onPause()
                }

                override fun onStop() {
                    persistGame()
                    super.onStop()
                }

                override fun onWindowFocusChanged(hasFocus: Boolean) {
                    super.onWindowFocusChanged(hasFocus)
                    if (hasFocus) {
                        window.decorView.post { showSystemBars() }
                    }
                }

                override suspend fun activityMain() {
                    val manager = SaveManager(this)
                    IslandSession.saveManager = manager
                    IslandSession.save = runCatching { manager.load() }
                        .getOrDefault(SaveData())
                    IslandSession.saveDirty = false
                    islanddev.main()
                }

                private fun persistGame() {
                    runCatching {
                        runBlocking {
                            IslandSession.persist()
                        }
                    }
                }

                @Suppress("DEPRECATION")
                private fun installSystemBarGuard() {
                    window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
                        val hiddenFlags =
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                                View.SYSTEM_UI_FLAG_IMMERSIVE or
                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        if (visibility and hiddenFlags != 0) {
                            window.decorView.post { showSystemBars() }
                        }
                    }
                }

                @Suppress("DEPRECATION")
                private fun showSystemBars() {
                    window.clearFlags(
                        WindowManager.LayoutParams.FLAG_FULLSCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
                    )
                    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                    window.statusBarColor = Color.BLACK
                    window.navigationBarColor = Color.BLACK

                    val decorView = window.decorView
                    decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        window.setDecorFitsSystemWindows(true)
                        window.insetsController?.show(WindowInsets.Type.systemBars())
                        window.insetsController?.setSystemBarsAppearance(0, APPEARANCE_MASK)
                    }
                }

                private companion object {
                    const val APPEARANCE_MASK = 8 or 16
                }
            }
            """.trimIndent()
        )
    }
}
