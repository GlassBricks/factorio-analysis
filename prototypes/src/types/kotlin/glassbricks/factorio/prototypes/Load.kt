package glassbricks.factorio.prototypes

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.modules.SerializersModule
import java.io.InputStream

public val DataRawJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    // some hardcoded union handling workaround
    serializersModule = SerializersModule {
        polymorphicDefaultDeserializer(BVEnergySource::class) {
            if (it == null) BurnerEnergySource.serializer() else null
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
public fun loadDataRawFromStream(stream: InputStream): DataRaw {
    return DataRawJson.decodeFromStream(DataRaw.serializer(), stream)
}

public val SpaceAgeDataRaw: DataRaw by lazy {
    loadDataRawFromStream(
        DataRaw::class.java.getResourceAsStream("/vanilla-data-raw.json")
            ?: error("Missing resource vanilla-data-raw.json")
    )
}
