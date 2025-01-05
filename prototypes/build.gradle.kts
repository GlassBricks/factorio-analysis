plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}
val generatedDir = layout.buildDirectory.dir("generated").get()
val generatedSrcDir = generatedDir.dir("kotlin")
val generatedResourcesDir = generatedDir.dir("resources")

sourceSets {
    main {
        kotlin.srcDir(generatedSrcDir)
        resources.srcDir(generatedResourcesDir)
    }

    val codegen by creating
}

dependencies {
    api(libs.kotlinxSerializationJson)
    testImplementation(kotlin("test"))

    "codegenImplementation"(libs.kotlinxSerializationJson)
    "codegenImplementation"("com.squareup:kotlinpoet:1.17.0")
}

kotlin {
    explicitApi()
}

tasks {
    val generatePrototypes by registering(JavaExec::class) {
        group = "generate"
        mainClass = "glassbricks.factorio.prototypecodegen.MainKt"
        classpath = sourceSets["codegen"].runtimeClasspath
        args = listOf(generatedSrcDir.asFile.absolutePath)
        outputs.dir(generatedSrcDir)
    }

    compileKotlin { dependsOn(generatePrototypes) }
}
