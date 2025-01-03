package glassbricks.factorio.prototypecodegen

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.net.URI


@OptIn(ExperimentalSerializationApi::class)
fun main() {
    val source = "https://lua-api.factorio.com/latest/prototype-api.json"
    val out = File("prototypes/src/codegen/resources/prototype-api.json")

    val json = Json { prettyPrint = true }
    val data = json.decodeFromStream<JsonElement>(URI(source).toURL().openStream())

    println("version: ${data.jsonObject["application_version"]}")

    out.parentFile.mkdirs()
    out.writeText(json.encodeToString(data))
}