package wtrack.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
            pluginManager.apply("com.google.dagger.hilt.android")
            pluginManager.apply("com.google.devtools.ksp")

            val keystorePropertiesFile = rootProject.file("keystore.properties")
            val keystoreProps = if (keystorePropertiesFile.isFile) {
                Properties().apply { keystorePropertiesFile.inputStream().use(::load) }
            } else {
                null
            }
            val storeFilePath = keystoreProps?.getProperty("storeFile")?.trim().orEmpty()
            val storePass = keystoreProps?.getProperty("storePassword").orEmpty()
            val releaseKeyAlias = keystoreProps?.getProperty("keyAlias")?.trim().orEmpty()
            val releaseKeyPass = keystoreProps?.getProperty("keyPassword").orEmpty()
            val hasReleaseSigning = storeFilePath.isNotEmpty() &&
                storePass.isNotEmpty() &&
                releaseKeyAlias.isNotEmpty() &&
                releaseKeyPass.isNotEmpty() &&
                rootProject.file(storeFilePath).isFile

            val android = extensions.getByType(ApplicationExtension::class.java)
            android.apply {
                compileSdk = 37
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                if (hasReleaseSigning) {
                    signingConfigs {
                        create("release") {
                            storeFile = rootProject.file(storeFilePath)
                            storePassword = storePass
                            keyAlias = releaseKeyAlias
                            keyPassword = releaseKeyPass
                        }
                    }
                }
                buildTypes {
                    getByName("release") {
                        isMinifyEnabled = true
                        isShrinkResources = true
                        if (hasReleaseSigning) {
                            signingConfig = signingConfigs.getByName("release")
                        }
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                    }
                }
                buildFeatures {
                    compose = true
                    buildConfig = true
                }
                lint {
                    abortOnError = true
                    warningsAsErrors = false
                    checkDependencies = false
                }
                defaultConfig {
                    applicationId = "wtrack.app"
                    minSdk = 26
                    targetSdk = 36
                    val versionCodeProp = providers.gradleProperty("wtrack.versionCode").orNull?.toIntOrNull()
                    val versionNameProp = providers.gradleProperty("wtrack.versionName").orNull?.takeIf { it.isNotBlank() }
                    versionCode = versionCodeProp ?: 1010099
                    versionName = versionNameProp ?: "1.1.0"
                    buildConfigField("String", "REPO_URL", "\"https://github.com/Adomancer/W-Track\"")
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    vectorDrawables.useSupportLibrary = true
                }
            }

            val kotlin = extensions.getByType(KotlinAndroidProjectExtension::class.java)
            kotlin.compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }
}

class KotlinJvmConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")

            val java = extensions.getByType(JavaPluginExtension::class.java)
            java.sourceCompatibility = JavaVersion.VERSION_17
            java.targetCompatibility = JavaVersion.VERSION_17

            val kotlin = extensions.getByType(KotlinJvmProjectExtension::class.java)
            kotlin.compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }
}
