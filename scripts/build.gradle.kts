plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    api(project(":factorio-recipe-calculator"))
}

// flatten source sets to just /src
sourceSets.main {
    kotlin {
        srcDirs("src")
    }
}
