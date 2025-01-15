plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.orTools)
    testImplementation(libs.bundles.kotest)
    testImplementation(kotlin("test"))
}
