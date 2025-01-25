plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinxAtomicfu)
}

dependencies {
    api(libs.bundles.kotlinxEcosystem)
    api(libs.orTools)
    testImplementation(libs.bundles.kotest)
    testImplementation(kotlin("test"))
}
