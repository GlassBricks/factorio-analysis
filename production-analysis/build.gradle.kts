plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinxAtomicfu)
}

dependencies {
    api(libs.bundles.kotlinxEcosystem)
    api(libs.orTools)
    api(libs.bundles.kotlinxEcosystem)
    testImplementation(libs.bundles.kotest)
    testImplementation(kotlin("test"))
}
