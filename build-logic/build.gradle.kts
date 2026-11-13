plugins {
    `kotlin-dsl`
}

group = "wtrack.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly("com.android.tools.build:gradle:9.5.1")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.21")
    compileOnly("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.2.21")
    compileOnly("com.google.dagger:hilt-android-gradle-plugin:2.60.1")
    compileOnly("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.2.21-2.0.4")
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "wtrack.android.application"
            implementationClass = "wtrack.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "wtrack.android.library"
            implementationClass = "wtrack.buildlogic.AndroidLibraryConventionPlugin"
        }
    }
}