package glassbricks.factorio.prototypecodegen

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlin.io.path.Path
import kotlin.io.path.absolute

@OptIn(ExperimentalSerializationApi::class)
fun readDocs(): PrototypeApiDocs {
    val stream = object {}.javaClass.getResourceAsStream("/prototype-api.json")!!
    val json = Json { ignoreUnknownKeys = true }
    return json.decodeFromStream<PrototypeApiDocs>(stream)
        .also {
            assert(it.api_version == 6)
        }
}

private const val defaultArgs = "prototypes/build/generated/kotlin"

fun main(args: Array<String>) {
    val outPath = Path(args.firstOrNull() ?: defaultArgs)
    readDocs()
        .let { Generator(it).generate() }
        .forEach {
            it.writeTo(outPath)
        }
    println("Generated to ${outPath.absolute()}")
}
