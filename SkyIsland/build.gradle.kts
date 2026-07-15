import korlibs.korge.gradle.*

plugins {
    id("com.soywiz.korge") version "6.0.0"
    id("com.google.devtools.ksp") version "2.0.20-1.0.25"
}

korge {
    id = "com.example.skyisland"
    name = "SkyIsland"
    description = "Turn based grid roguelike on floating islands"
    version = "0.1.0"
    entryPoint = "skyisland.ui.main"
    androidCompileSdk = 34
    androidTargetSdk = 34
    orientation = Orientation.PORTRAIT
    targetJvm()
    targetAndroid()
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation("androidx.room:room-runtime:2.6.1")
        }
    }
}

dependencies {
    add("kspAndroid", "androidx.room:room-compiler:2.6.1")
}

// kotlin-stdlib-common was merged into kotlin-stdlib in Kotlin 2.0.
// Korge resolves KorgeReloadAgent during config phase, so configurations.all fails.
// configureEach is lazy; skip already-resolved configs to avoid the mutation error.
configurations.configureEach {
    if (state == org.gradle.api.artifacts.Configuration.State.UNRESOLVED) {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-stdlib-common") {
                useTarget("org.jetbrains.kotlin:kotlin-stdlib:${requested.version}")
            }
        }
    }
}

// Korge's direct Android target is a KMP root project. AGP lint attempts to
// resolve that root as an Android unit-test artifact and fails variant matching.
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
