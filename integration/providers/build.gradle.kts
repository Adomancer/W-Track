plugins {
    id("wtrack.android.library")
}

android {
    namespace = "wtrack.integration.providers"
}

dependencies {
    implementation(project(":integration:api"))
    testImplementation(libs.junit)
    testImplementation(libs.com.google.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}