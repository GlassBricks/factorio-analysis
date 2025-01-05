package glassbricks.factorio.prototypes

import kotlin.math.ceil
import kotlin.math.roundToInt


public val EntityPrototype.energySource: EnergySource? get() = (this as? HasEnergySource)?.energy_source
public val EntityPrototype.usesElectricity: Boolean get() = energySource is ElectricEnergySource

public val EntityPrototype.effectiveTileWidth: Int
    get() = this.tile_width?.toInt() ?: this.collision_box?.let { ceil(it.width).toInt() } ?: 1


public val EntityPrototype.effectiveTileHeight: Int
    get() = this.tile_height?.toInt() ?: this.collision_box?.let { ceil(it.height).toInt() } ?: 1

public val EntityPrototype.effectiveCollisionMask: CollisionMask
    get() = this.collision_mask ?: CollisionMask.DEFAULT_MASKS[this.type] ?: CollisionMask.EMPTY

/**
 * Given an entity prototype that snaps to grid, returns the position of the entity if its upper left tile were at the given position.
 *
 * Note that this completely ignores the `off-grid` flag.
 */
public fun EntityPrototype.tileSnappedPosition(
    topLeftTile: TilePosition,
    direction: Direction = Direction.North
): Position {
    val isRotated = direction == Direction.East || direction == Direction.West
    val (x, y) = topLeftTile
    return if (!isRotated) Position(x + effectiveTileWidth / 2.0, y + effectiveTileHeight / 2.0)
    else Position(x + effectiveTileHeight / 2.0, y + effectiveTileWidth / 2.0)
}

/**
 * Given the position of an entity that snaps to grid, returns the top left tile of the entity.
 */
public fun EntityPrototype.topLeftTileAt(
    entityPosition: Position,
    direction: Direction = Direction.North
): TilePosition {
    val isRotated = direction == Direction.East || direction == Direction.West
    val (x, y) = entityPosition
    return if (!isRotated) TilePosition(
        (x - effectiveTileWidth / 2.0).roundToInt(),
        (y - effectiveTileHeight / 2.0).roundToInt()
    )
    else TilePosition((x - effectiveTileHeight / 2.0).roundToInt(), (y - effectiveTileWidth / 2.0).roundToInt())
}
