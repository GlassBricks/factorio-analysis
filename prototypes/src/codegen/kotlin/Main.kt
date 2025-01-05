package glassbricks.factorio.prototypecodegen

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

@OptIn(ExperimentalSerializationApi::class)
fun readDocs(): PrototypeApiDocs {
    val stream = object {}.javaClass.getResourceAsStream("/prototype-api.json")!!
    val json = Json { ignoreUnknownKeys = true }
    return json.decodeFromStream<PrototypeApiDocs>(stream)
}

fun main(args: Array<String>) {
    val outPath = Path(args.firstOrNull() ?: "prototypes/build/generated/kotlin")
    println("Generating prototypes to ${outPath.absolutePathString()}")
    readDocs()
        .let {
            GeneratedPrototypesBuilder(it).apply { classesToGenerate() }.build()
        }
        .let { PrototypeDeclarationsGenerator(it).generate() }
        .forEach {
            it.writeTo(outPath)
        }
}
