package glassbricks.factorio.blueprint.prototypes

import glassbricks.factorio.blueprint.BoundingBox
import glassbricks.factorio.blueprint.Position
import glassbricks.factorio.blueprint.json.DoubleAsIntSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*


private val doubleAsIntListSerializer = ListSerializer(DoubleAsIntSerializer)

public object PositionShorthandSerializer : KSerializer<Position> {
    override val descriptor: SerialDescriptor = doubleAsIntListSerializer.descriptor

    override fun deserialize(decoder: Decoder): Position {
        decoder as JsonDecoder
        val x: Double
        val y: Double
        when (val element = decoder.decodeJsonElement()) {
            is JsonObject -> {
                x = element["x"]?.jsonPrimitive?.doubleOrNull ?: error("Expected x in position")
                y = element["y"]?.jsonPrimitive?.doubleOrNull ?: error("Expected y in position")
            }

            is JsonArray -> {
                x = element.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: error("Expected x in position tuple")
                y = element.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: error("Expected y in position tuple")
            }

            else -> throw SerializationException("Unexpected json for Position: $element")
        }
        return Position(x, y)
    }


    override fun serialize(encoder: Encoder, value: Position) {
        encoder.encodeSerializableValue(
            doubleAsIntListSerializer,
            listOf(value.x, value.y)
        )
    }
}

public object BoundingBoxShorthandSerializer : KSerializer<BoundingBox> {
    override val descriptor: SerialDescriptor = ListSerializer(Position.serializer()).descriptor

    override fun deserialize(decoder: Decoder): BoundingBox {
        decoder as JsonDecoder
        val leftTop: JsonElement
        val rightBottom: JsonElement
        when (val element = decoder.decodeJsonElement()) {
            is JsonObject -> {
                leftTop = element["left_top"] ?: error("Expected left_top in bounding box")
                rightBottom = element["right_bottom"] ?: error("Expected right_bottom in bounding box")
            }

            is JsonArray -> {
                leftTop = element.getOrNull(0) ?: error("Expected left_top in bounding box tuple")
                rightBottom = element.getOrNull(1) ?: error("Expected right_bottom in bounding box tuple")
            }

            else -> throw SerializationException("Unexpected json for BoundingBox: $element")
        }
        return BoundingBox(
            decoder.json.decodeFromJsonElement(PositionShorthandSerializer, leftTop),
            decoder.json.decodeFromJsonElement(PositionShorthandSerializer, rightBottom)
        )
    }

    override fun serialize(encoder: Encoder, value: BoundingBox) {
        encoder.encodeSerializableValue(
            ListSerializer(PositionShorthandSerializer),
            listOf(value.leftTop, value.rightBottom)
        )
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
