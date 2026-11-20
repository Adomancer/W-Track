plugins {
    id("wtrack.kotlin.jvm")
}

dependencies {
    implementation(project(":integration:api"))
    testImplementation(libs.junit)
    testImplementation(libs.com.google.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
