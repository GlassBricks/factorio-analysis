package glassbricks.factorio.prototypes.generate

import glassbricks.factorio.prototypes.DataRaw
import glassbricks.factorio.prototypes.DataRawJson
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File


@OptIn(ExperimentalSerializationApi::class)
fun main(args: Array<String>) {
    val outputFile = args.getOrNull(0) ?: error("Missing output file argument")
    val dataRawDump =
        object {}.javaClass.getResource("/data-raw-dump.json") ?: error("Missing resource data-raw-dump.json")
    val dataRaw = DataRawJson.decodeFromStream<DataRaw>(dataRawDump.openStream())
    File(outputFile).outputStream().use {
        DataRawJson.encodeToStream(DataRaw.serializer(), dataRaw, it)
    }
}
