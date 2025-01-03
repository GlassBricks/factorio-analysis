package glassbricks.factorio.prototypecodegen

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.File

@OptIn(ExperimentalSerializationApi::class)
fun readDocs(): PrototypeApiDocs {
    val stream = object {}.javaClass.getResourceAsStream("/prototype-api.json")!!
    val json = Json { ignoreUnknownKeys = true }
    return json.decodeFromStream<PrototypeApiDocs>(stream)
        .also {
            assert(it.api_version == 6)
        }
}

fun main(args: Array<String>) {
    val outPath = requireNotNull(args.firstOrNull()) { "output path required" }
    readDocs()
        .let { Generator(it).generate() }
        .writeTo(File(outPath))
    println("Generated to ${File(outPath).absolutePath}")
}
