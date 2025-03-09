plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    api(project(":factorio-recipe-calculator"))
    testImplementation(libs.bundles.kotest)
    testImplementation(kotlin("test"))
}

// flatten source sets to just /src
sourceSets {
    main {
        kotlin {
            srcDirs("src")
        }
    }
    test {
        kotlin {
            srcDirs("test")
        }
    }
}
