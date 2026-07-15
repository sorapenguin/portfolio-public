import korlibs.korge.gradle.DisplayCutout
import korlibs.korge.gradle.Orientation

plugins {
    id("com.soywiz.korge")
    id("org.jetbrains.kotlin.plugin.serialization")
}

korge {
    id = "dev.sorapenguin.starsaga"
    name = "StarSaga"
    description = "Monster collection RPG built with KorGE"
    version = "0.1.0"
    entryPoint = "starsaga.main"
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

        activityFile.parentFile.mkdirs()
        activityFile.writeText(
            """
            package dev.sorapenguin.starsaga

            import android.graphics.Color
            import android.os.Build
            import android.os.Bundle
            import android.view.View
            import android.view.WindowInsets
            import android.view.WindowManager
            import korlibs.render.GameWindowCreationConfig
            import korlibs.render.KorgwActivity
            import starsaga.StarSagaSession
            import starsaga.main
            import starsaga.save.SaveManager

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

                override fun onWindowFocusChanged(hasFocus: Boolean) {
                    super.onWindowFocusChanged(hasFocus)
                    if (hasFocus) {
                        window.decorView.post { showSystemBars() }
                    }
                }

                override suspend fun activityMain() {
                    StarSagaSession.saveManager = SaveManager(this)
                    starsaga.main()
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

                    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE

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
