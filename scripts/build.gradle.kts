plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":factorio-recipe-analysis"))
}

// flatten source sets to just /src
sourceSets.main {
    kotlin {
        srcDirs("src")
    }

}
