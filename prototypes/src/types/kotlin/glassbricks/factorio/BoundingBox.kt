package glassbricks.factorio

import glassbricks.factorio.Direction.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Represents an axis-aligned bounding box.
 *
 * Intersection checks are inclusive of lower bounds, exclusive of upper bounds.
 */
@Serializable
public data class BoundingBox(
    @SerialName("left_top")
    public val leftTop: Position,
    @SerialName("right_bottom")
    public val rightBottom: Position,
) {
    public constructor(minX: Double, minY: Double, maxX: Double, maxY: Double) :
            this(Position(minX, minY), Position(maxX, maxY))

    public val minX: Double get() = leftTop.x
    public val minY: Double get() = leftTop.y
    public val maxX: Double get() = rightBottom.x
    public val maxY: Double get() = rightBottom.y

    public val width: Double get() = maxX - minX
    public val height: Double get() = maxY - minY

    public val size: Position get() = Position(width, height)

    /** Returns true if the given point is inside the bounding box; all bounds are exclusive. */
    public operator fun contains(point: Position): Boolean =
        point.x in minX..<maxX && point.y in minY..<maxY

    /**
     * Returns true if this bounding box fully contains the given bounding box.
     *
     * Touching edges are considered contained.
     */
    public operator fun contains(other: BoundingBox): Boolean =
        minX <= other.minX && minY <= other.minY && maxX >= other.maxX && maxY >= other.maxY

    /** Returns true if the given bounding box intersects with this one. Touching boxes are not considered intersecting. */
    public infix fun intersects(other: BoundingBox): Boolean =
        minX < other.maxX && maxX > other.minX && minY < other.maxY && maxY > other.minY

    public fun translate(amount: Position): BoundingBox = BoundingBox(leftTop + amount, rightBottom + amount)
    public fun translate(x: Double, y: Double): BoundingBox =
        BoundingBox(leftTop + Position(x, y), rightBottom + Position(x, y))

    /**
     * If the given direction is not a cardinal direction, will snap to the next lowest cardinal direction.
     *
     * North is up; +y is down, +x is right.
     */
    public fun rotateCardinal(direction: Direction): BoundingBox = when (direction) {
        North, Northeast -> this
        East, Southeast -> BoundingBox(-maxY, minX, -minY, maxX)
        South, Southwest -> BoundingBox(-maxX, -maxY, -minX, -minY)
        West, Northwest -> BoundingBox(minY, -maxX, maxY, -minX)
    }

    /**
     * Expands the bounding box by the given amount in all directions.
     */
    public fun expand(amount: Double): BoundingBox =
        BoundingBox(minX - amount, minY - amount, maxX + amount, maxY + amount)

    override fun toString(): String = "BoundingBox(pos($minX, $minY), pos($maxX, $maxY))"

    public fun center(): Position = Position((minX + maxX) / 2, (minY + maxY) / 2)
    public fun centerVec(): Vector = Vector((minX + maxX) / 2, (minY + maxY) / 2)

    public companion object {
        public fun around(
            point: Position,
            radius: Double,
        ): BoundingBox = bbox(point.x - radius, point.y - radius, point.x + radius, point.y + radius)

        public val EMPTY: BoundingBox = BoundingBox(Position.ZERO, Position.ZERO)
    }
}

public fun getEnclosingBox(boxes: Iterable<BoundingBox>): BoundingBox {
    var minX = Double.POSITIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    for (box in boxes) {
        minX = minOf(minX, box.minX)
        minY = minOf(minY, box.minY)
        maxX = maxOf(maxX, box.maxX)
        maxY = maxOf(maxY, box.maxY)
    }
    return bbox(minX, minY, maxX, maxY)
}

public fun bbox(
    minX: Double,
    minY: Double,
    maxX: Double,
    maxY: Double,
): BoundingBox = BoundingBox(minX, minY, maxX, maxY)

public fun bbox(leftTop: Position, rightBottom: Position): BoundingBox = BoundingBox(leftTop, rightBottom)

public data class TileBoundingBox(
    val leftTop: TilePosition,
    val rightBottomExclusive: TilePosition,
) : Collection<TilePosition> {
    public constructor(minX: Int, minY: Int, maxX: Int, maxY: Int) :
            this(TilePosition(minX, minY), TilePosition(maxX, maxY))

    public val minX: Int get() = leftTop.x
    public val minY: Int get() = leftTop.y
    public val maxXExclusive: Int get() = rightBottomExclusive.x
    public val maxYExclusive: Int get() = rightBottomExclusive.y

    val width: Int get() = maxXExclusive - minX
    val height: Int get() = maxYExclusive - minY

    override operator fun contains(element: TilePosition): Boolean =
        element.x in minX..<maxXExclusive && element.y in minY..<maxYExclusive

    override val size: Int
        get() = width * height

    public fun expand(amount: Int): TileBoundingBox = TileBoundingBox(
        TilePosition(minX - amount, minY - amount),
        TilePosition(maxXExclusive + amount, maxYExclusive + amount)
    )

    override fun containsAll(elements: Collection<TilePosition>): Boolean = elements.all { it in this }

    override fun isEmpty(): Boolean = width == 0 || height == 0

    override fun iterator(): Iterator<TilePosition> = object : Iterator<TilePosition> {
        private var current = leftTop
        override fun hasNext(): Boolean = current.y < maxYExclusive && current.x < maxXExclusive
        override fun next(): TilePosition {
            val result = current
            current = if (current.x + 1 < maxXExclusive) TilePosition(current.x + 1, current.y)
            else TilePosition(minX, current.y + 1)
            return result
        }
    }
}

public fun BoundingBox.roundOutToTileBbox(): TileBoundingBox =
    TileBoundingBox(
        floor(minX).toInt(),
        floor(minY).toInt(),
        ceil(maxX).toInt(),
        ceil(maxY).toInt()
    )

public fun TileBoundingBox.toBoundingBox(): BoundingBox =
    bbox(minX.toDouble(), minY.toDouble(), maxXExclusive.toDouble(), maxYExclusive.toDouble())

public fun tileBbox(
    minX: Int,
    minY: Int,
    maxXExclusive: Int,
    maxYExclusive: Int,
): TileBoundingBox = TileBoundingBox(minX, minY, maxXExclusive, maxYExclusive)

public fun tileBbox(leftTop: TilePosition, rightBottomExclusive: TilePosition): TileBoundingBox =
    TileBoundingBox(leftTop, rightBottomExclusive)
