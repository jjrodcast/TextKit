import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.maven.publish)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    jvm()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    androidLibrary {
        namespace = "com.jjrodcast.textkit.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            // Decompose
            implementation(libs.decompose)
            implementation(libs.essenty.lifecycle)

            // Serialization
            implementation(libs.jetbrains.kotlinx.serialization.core)
            implementation(libs.jetbrains.kotlinx.serialization.json)

            // Icons
            implementation(libs.jetbrains.compose.material.icons)
            implementation(libs.jetbrains.compose.material.icons.extended)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jsMain.dependencies {
            implementation(libs.wrappers.browser)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(
        groupId = "io.github.jjrodcast",
        artifactId = "textkit",
        // Version comes from the pushed git tag (e.g. tag `v1.0.0-alpha01` -> `1.0.0-alpha01`).
        // Falls back to a SNAPSHOT for local builds when no -PlibVersion is provided.
        version = (findProperty("libVersion") as String?) ?: "1.0.0-SNAPSHOT"
    )

    pom {

        name = "Text Kit"
        description = "Rich Text Editor engine for Compose Multiplatform"
        inceptionYear = "2026"
        url = "https://github.com/jjrodcast/TextKit"

        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/licenses/MIT"
                distribution = "repo"
            }
        }

        developers {
            developer {
                id = "jjrodcast"
                name = "Jorge Rodriguez"
                url = "https://github.com/jjrodcast/jjrodcast"
            }
        }

        scm {
            url = "https://github.com/jjrodcast/TextKit"
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}