import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode.Strict
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}
val generatedDir = layout.buildDirectory.dir("generated").get()
val generatedSrcDir = generatedDir.dir("kotlin")
val generatedResourcesDir = generatedDir.dir("resources")

sourceSets {
    val codegen by creating

    val types by creating {
        kotlin.srcDir(generatedSrcDir)
    }

    val generateResources by creating {
        compileClasspath += types.compileClasspath
        runtimeClasspath += types.runtimeClasspath
    }

    main {
        resources.srcDir(generatedResourcesDir)
    }
}

dependencies {
    // codeGen <- (Types) <- generateResources
    //               ^---------^---- main
    "codegenImplementation"(libs.kotlinxSerializationJson)
    "codegenImplementation"("com.squareup:kotlinpoet:1.17.0")

    "typesApi"(libs.kotlinxSerializationJson)

    "generateResourcesImplementation"(sourceSets["types"].output)

    api(libs.kotlinxSerializationJson)
    api(sourceSets["types"].output)

    testImplementation(kotlin("test"))
}

kotlin {
    explicitApi()
}

tasks {

    val codeGen by registering(JavaExec::class) {
        group = "generate"
        mainClass = "glassbricks.factorio.prototypecodegen.MainKt"
        classpath = sourceSets["codegen"].runtimeClasspath
        args = listOf(generatedSrcDir.asFile.absolutePath)
        outputs.dir(generatedSrcDir)
    }

    "compileTypesKotlin" {
        this as KotlinCompile
        dependsOn(codeGen)
        compilerOptions {
            explicitApiMode.set(Strict)
        }
    }

    val generateVanillaDataRaw by registering(JavaExec::class) {
        group = "generate"
        mainClass = "glassbricks.factorio.prototypes.generate.MainKt"
        classpath = sourceSets["generateResources"].runtimeClasspath

        val outputFile = generatedResourcesDir.file("vanilla-data-raw.json")

        args = listOf(outputFile.asFile.absolutePath)
        outputs.file(outputFile)
    }

    processResources { dependsOn(generateVanillaDataRaw) }
}
