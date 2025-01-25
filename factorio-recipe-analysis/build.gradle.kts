plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    api(project(":production-analysis"))
    api(project(":prototypes"))
    testImplementation(libs.bundles.kotest)
    testImplementation(kotlin("test"))
}
