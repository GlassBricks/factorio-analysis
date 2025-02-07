plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    api(project(":recipe-lp"))
    api(project(":prototypes"))
    testImplementation(libs.bundles.kotest)
    testImplementation(kotlin("test"))
}
