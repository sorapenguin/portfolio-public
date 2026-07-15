import com.android.build.api.dsl.ApplicationExtension
import korlibs.korge.gradle.Orientation
import org.gradle.api.tasks.JavaExec

plugins {
    id("com.soywiz.korge")
}

korge {
    id = "dev.sorapenguin.starterra"
    name = "StarTerra"
    description = "Batch A minimal 2.5D rendering spike"
    version = "0.1.0"
    entryPoint = "starterra.main"
    androidCompileSdk = 34
    androidTargetSdk = 34
    orientation = Orientation.PORTRAIT
    targetJvm()
    targetAndroid()
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

extensions.configure<ApplicationExtension> {
    buildFeatures {
        buildConfig = true
    }
}

// KorGE's generated JVM run task needs the Kotlin file facade explicitly in this project.
gradle.projectsEvaluated {
    tasks.named<JavaExec>("jvmRun") {
        mainClass.set("starterra.MainKt")
    }
}

// KorGE generates the Android Activity/theme during createAndroidManifest. Keep the
// override here (not in build/) so it survives every Android build.
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
                    <item name="android:statusBarColor">#101923</item>
                    <item name="android:navigationBarColor">#101923</item>
                    <item name="android:windowLightStatusBar">false</item>
                    <item name="android:windowLightNavigationBar">false</item>
                </style>
            </resources>
            """.trimIndent()
        )

        activityFile.parentFile.mkdirs()
        activityFile.writeText(
            """
            package dev.sorapenguin.starterra

            import android.graphics.Color
            import android.os.Build
            import android.os.Bundle
            import android.view.View
            import android.view.WindowInsets
            import android.view.WindowManager
            import korlibs.render.GameWindowCreationConfig
            import korlibs.render.KorgwActivity
            import starterra.main
            import starterra.save.AndroidOutpostSaveStore

            class MainActivity : KorgwActivity(
                config = GameWindowCreationConfig(msaa = 1, fullscreen = false)
            ) {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    showSystemBars()
                }

                override fun onResume() {
                    super.onResume()
                    window.decorView.post { showSystemBars() }
                }

                override fun onWindowFocusChanged(hasFocus: Boolean) {
                    super.onWindowFocusChanged(hasFocus)
                    if (hasFocus) window.decorView.post { showSystemBars() }
                }

                override suspend fun activityMain() {
                    AndroidOutpostSaveStore.initialize(applicationContext)
                    starterra.main()
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
                    window.statusBarColor = Color.rgb(16, 25, 35)
                    window.navigationBarColor = Color.rgb(16, 25, 35)
                    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        window.setDecorFitsSystemWindows(true)
                        window.insetsController?.show(WindowInsets.Type.systemBars())
                        window.insetsController?.setSystemBarsAppearance(0, LIGHT_BAR_APPEARANCE)
                    }
                }

                private companion object {
                    const val LIGHT_BAR_APPEARANCE = 8 or 16
                }
            }
            """.trimIndent()
        )
    }
}
