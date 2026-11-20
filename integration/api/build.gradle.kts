plugins {
    id("wtrack.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.com.google.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
