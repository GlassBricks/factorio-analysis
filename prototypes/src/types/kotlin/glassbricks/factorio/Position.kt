package glassbricks.factorio

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlin.math.absoluteValue
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Stored as 32-bit numbers, with 8 bits for the fractional part.
 *
 * [x] and [y] hold the values as doubles, you can normally use those.
 */
@Serializable(with = Position.Serializer::class)
public class Position private constructor(
    public val xAsInt: Int,
    public val yAsInt: Int,
) : Comparable<Position> {
    public constructor(x: Double, y: Double) : this((x * 256).roundToInt(), (y * 256).roundToInt())

    public val x: Double get() = xAsInt / 256.0
    public val y: Double get() = yAsInt / 256.0

    public operator fun plus(other: Position): Position = Position(xAsInt + other.xAsInt, yAsInt + other.yAsInt)
    public operator fun minus(other: Position): Position = Position(xAsInt - other.xAsInt, yAsInt - other.yAsInt)

    public operator fun times(scale: Double): Position =
        Position((xAsInt * scale).roundToInt(), (yAsInt * scale).roundToInt())

    public operator fun times(scale: Int): Position = Position(xAsInt * scale, yAsInt * scale)
    public operator fun div(scale: Double): Position =
        Position((xAsInt / scale).roundToInt(), (yAsInt / scale).roundToInt())

    public operator fun div(scale: Int): Position = Position(xAsInt / scale, yAsInt / scale)

    public operator fun unaryPlus(): Position = this
    public operator fun unaryMinus(): Position = Position(-xAsInt, -yAsInt)

    public fun squaredLength(): Double = (xAsInt.toLong() * xAsInt + yAsInt.toLong() * yAsInt) / (256.0 * 256.0)
    public fun length(): Double = sqrt(squaredLength())

    public fun squaredDistanceTo(other: Position): Double = (this - other).squaredLength()
    public fun distanceTo(other: Position): Double = (this - other).length()

    /**
     * Returns the tile position of the tile this position is in.
     */
    public fun occupiedTile(): TilePosition = TilePosition(floor(xAsInt / 256.0).toInt(), floor(y).toInt())

    public fun isZero(): Boolean = xAsInt == 0 && yAsInt == 0

    public fun rotateCardinal(direction: Direction): Position = when (direction) {
        Direction.North, Direction.Northeast -> this
        Direction.East, Direction.Southeast -> Position(-yAsInt, xAsInt)
        Direction.South, Direction.Southwest -> Position(-xAsInt, -yAsInt)
        Direction.West, Direction.Northwest -> Position(yAsInt, -xAsInt)
    }

    public operator fun component1(): Double = x
    public operator fun component2(): Double = y

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Position) return false
        return xAsInt == other.xAsInt && yAsInt == other.yAsInt
    }

    override fun hashCode(): Int = 31 * xAsInt + yAsInt
    override fun toString(): String = "pos($x, $y)"

    override fun compareTo(other: Position): Int {
        val xComp = xAsInt.compareTo(other.xAsInt)
        return if (xComp != 0) xComp else yAsInt.compareTo(other.yAsInt)
    }

    public companion object {
        public val ZERO: Position = Position(0, 0)
    }

    internal object Serializer : KSerializer<Position> {
        @OptIn(ExperimentalSerializationApi::class)
        override val descriptor: SerialDescriptor =
            SerialDescriptor(Position::class.qualifiedName!!, Vector.serializer().descriptor)

        override fun deserialize(decoder: Decoder): Position =
            decoder.decodeSerializableValue(Vector.serializer())
                .let { Position(it.x, it.y) }

        override fun serialize(encoder: Encoder, value: Position) {
            encoder.encodeSerializableValue(Vector.serializer(), Vector(value.x, value.y))
        }
    }
}

public operator fun Double.times(position: Position): Position = position * this
public operator fun Int.times(position: Position): Position = position * this

/** Short for [Position] constructor. */
public fun pos(x: Double, y: Double): Position = Position(x, y)

/**
 * Like [Position], but represents only tiles as integers.
 *
 * The top left corner of a tile is the tile's position as a MapPosition.
 */
@Serializable
public data class TilePosition(val x: Int, val y: Int) : Comparable<TilePosition> {
    public operator fun plus(other: TilePosition): TilePosition = TilePosition(x + other.x, y + other.y)
    public operator fun minus(other: TilePosition): TilePosition = TilePosition(x - other.x, y - other.y)

    public operator fun times(scale: Int): TilePosition = TilePosition(x * scale, y * scale)

    public operator fun unaryMinus(): TilePosition = TilePosition(-x, -y)
    public operator fun unaryPlus(): TilePosition = TilePosition(x, y)

    public fun add(x: Int, y: Int): TilePosition = TilePosition(this.x + x, this.y + y)

    public fun squaredLength(): Int = x * x + y * y
    public fun length(): Double = sqrt(squaredLength().toDouble())

    public fun tileCenter(): Position = Position(x + 0.5, y + 0.5)
    public fun tileTopLeft(): Position = Position(x.toDouble(), y.toDouble())

    public fun isZero(): Boolean = x == 0 && y == 0

    /** Gets the map position bounding box of this tile. */
    public fun tileBoundingBox(): BoundingBox = BoundingBox(pos(x.toDouble(), y.toDouble()), pos(x + 1.0, y + 1.0))

    public fun manhattanDistanceTo(other: TilePosition): Int = (x - other.x).absoluteValue + (y - other.y).absoluteValue

    override fun compareTo(other: TilePosition): Int {
        val xComp = x.compareTo(other.x)
        return if (xComp != 0) xComp else y.compareTo(other.y)
    }

    public companion object {
        public val ZERO: TilePosition = TilePosition(0, 0)
    }
}

public operator fun Int.times(position: TilePosition): TilePosition = position * this

public fun tilePos(x: Int, y: Int): TilePosition = TilePosition(x, y)

/** A vector that truly uses doubles, unlike [Position]. */
@Serializable(with = Vector.Serializer::class)
public data class Vector(
    @Serializable(with = DoubleAsIntSerializer::class)
    val x: Double,
    @Serializable(with = DoubleAsIntSerializer::class)
    val y: Double,
) {
    public operator fun plus(other: Vector): Vector = Vector(x + other.x, y + other.y)
    public operator fun minus(other: Vector): Vector = Vector(x - other.x, y - other.y)

    public operator fun times(scale: Double): Vector = Vector(x * scale, y * scale)
    public operator fun div(scale: Double): Vector = Vector(x / scale, y / scale)

    public operator fun unaryMinus(): Vector = Vector(-x, -y)
    public operator fun unaryPlus(): Vector = this

    public fun squaredLength(): Double = x * x + y * y
    public fun length(): Double = sqrt(squaredLength())

    public fun distanceTo(other: Vector): Double = (this - other).length()

    public fun isZero(): Boolean = x == 0.0 && y == 0.0

    public fun rotateCardinal(direction: Direction): Vector = when (direction) {
        Direction.North, Direction.Northeast -> this
        Direction.East, Direction.Southeast -> Vector(-y, x)
        Direction.South, Direction.Southwest -> Vector(-x, -y)
        Direction.West, Direction.Northwest -> Vector(y, -x)
    }

    public fun rotate(direction: Direction): Vector = when (direction) {
        Direction.North -> this
        Direction.Northeast -> Vector(x * SIN45 - y * SIN45, x * SIN45 + y * SIN45)
        Direction.East -> Vector(-y, x)
        Direction.Southeast -> Vector(-x * SIN45 - y * SIN45, x * SIN45 - y * SIN45)
        Direction.South -> Vector(-x, -y)
        Direction.Southwest -> Vector(-x * SIN45 + y * SIN45, -x * SIN45 - y * SIN45)
        Direction.West -> Vector(y, -x)
        Direction.Northwest -> Vector(x * SIN45 + y * SIN45, -x * SIN45 + y * SIN45)
    }

    public fun closeTo(other: Vector, epsilon: Double = 1e-6): Boolean =
        (x - other.x).absoluteValue < epsilon && (y - other.y).absoluteValue < epsilon

    public companion object {
        private const val SIN45 = 0.7071067811865476 // = sqrt(2) / 2
        public val ZERO: Vector = Vector(0.0, 0.0)
    }

    internal object Serializer : KSerializer<Vector> {
        private val doubleAsIntListSerializer = ListSerializer(DoubleAsIntSerializer)
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Vector") {
            element<Double>("x")
            element<Double>("y")
        }

        override fun deserialize(decoder: Decoder): Vector {
            decoder as JsonDecoder
            val element = decoder.decodeJsonElement()
            val x: Double
            val y: Double
            when (element) {
                is JsonObject -> {
                    x = element["x"]!!.jsonPrimitive.double
                    y = element["y"]!!.jsonPrimitive.double
                }

                is JsonArray -> {
                    x = element[0].jsonPrimitive.double
                    y = element[1].jsonPrimitive.double
                }

                else -> throw SerializationException("Expected JsonObject or JsonArray")
            }
            return Vector(x, y)
        }

        override fun serialize(encoder: Encoder, value: Vector) {
            encoder.encodeSerializableValue(doubleAsIntListSerializer, listOf(value.x, value.y))
        }
    }
}

public fun Vector.toPosition(): Position = Position(x, y)
public fun Position.toVector(): Vector = Vector(x, y)

public operator fun Position.plus(vector: Vector): Position = Position(x + vector.x, y + vector.y)
public operator fun Position.minus(vector: Vector): Position = Position(x - vector.x, y - vector.y)

public operator fun Vector.plus(position: Position): Position = position + this
