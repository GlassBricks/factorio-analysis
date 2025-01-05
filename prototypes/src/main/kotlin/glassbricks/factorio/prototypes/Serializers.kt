package glassbricks.factorio.prototypes

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

public object DoubleAsIntSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DoubleAsInt", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double = decoder.decodeDouble()
    override fun serialize(encoder: Encoder, value: Double) {
        val asLong = value.toLong()
        if (asLong.toDouble() == value) {
            encoder.encodeLong(asLong)
        } else {
            encoder.encodeDouble(value)
        }
    }
}

public typealias ItemOrArray<T> = @Serializable(with = ItemOrArraySerializer::class) List<T>

public class ItemOrArraySerializer<T>(private val itemSerializer: KSerializer<T>) : KSerializer<ItemOrArray<T>> {
    private val listSerializer = ListSerializer(itemSerializer)
    override val descriptor: SerialDescriptor get() = listSerializer.descriptor

    override fun deserialize(decoder: Decoder): ItemOrArray<T> {
        decoder as JsonDecoder
        return when (val element = decoder.decodeJsonElement()) {
            is JsonArray -> decoder.json.decodeFromJsonElement(listSerializer, element)
            else -> listOf(decoder.json.decodeFromJsonElement(itemSerializer, element))
        }
    }

    override fun serialize(encoder: Encoder, value: ItemOrArray<T>) {
        if (value.size == 1) {
            encoder.encodeSerializableValue(itemSerializer, value[0])
        } else {
            encoder.encodeSerializableValue(listSerializer, value)
        }
    }
}

public class LuaListSerializer<T>(private val itemSerializer: KSerializer<T>) : KSerializer<List<T>> {
    private val listSerializer = ListSerializer(itemSerializer)
    override val descriptor: SerialDescriptor get() = listSerializer.descriptor
    override fun deserialize(decoder: Decoder): List<T> {
        decoder as JsonDecoder
        return when (val element = decoder.decodeJsonElement()) {
            is JsonArray -> decoder.json.decodeFromJsonElement(listSerializer, element)

            is JsonObject -> {
                val size = element.keys
                    .maxOfOrNull { key -> key.toIntOrNull() ?: 0 } ?: 0
                List(size) { index ->
                    val luaIndex = (index + 1).toString()
                    decoder.json.decodeFromJsonElement(itemSerializer, element[luaIndex] ?: JsonNull)
                }
            }

            else -> throw SerializationException("Unexpected json for LuaList: $element")
        }
    }

    override fun serialize(encoder: Encoder, value: List<T>) {
        listSerializer.serialize(encoder, value)
    }
}

/**
 * Indicates that a (primitive) property is not optional.
 *
 * This is only to get around the restriction that primitive types can't be lateinit.
 */
@Suppress("NOTHING_TO_INLINE")
public inline fun <T> required(placeholderValue: T): T = placeholderValue
