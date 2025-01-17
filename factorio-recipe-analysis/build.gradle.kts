plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":production-analysis"))
    implementation(project(":prototypes"))
    testImplementation(libs.bundles.kotest)
    testImplementation(kotlin("test"))
}
