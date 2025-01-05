package glassbricks.factorio.blueprint.prototypes

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.*


/**
 * Every entry in the array is a specification of one layer the object collides with or a special
 * collision option. Supplying an empty table means that no layers and no collision options are set.
 *
 * The default collision masks of all entity types can be found
 * [here](prototype:EntityPrototype::collision_mask). The base game provides common collision mask
 * functions in a Lua file in the core
 * [lualib](https://github.com/wube/factorio-data/blob/master/core/lualib/collision-mask-util.lua).
 *
 * Supplying an empty array means that no layers and no collision options are set.
 *
 * The three options in addition to the standard layers are not collision masks, instead they
 * control other aspects of collision.
 *
 * - `"not-colliding-with-itself"`: Any two entities that both have this option enabled on their prototype and have an identical collision mask layers list will not collide. Other collision mask options are not included in the identical layer list check. This does mean that two different prototypes with the same collision mask layers and this option enabled will not collide.
 *
 * The following exist in the game, but this library ignores them:
 *
 * - `"consider-tile-transitions"`: Uses the prototypes position rather than its collision box when doing collision checks with tile prototypes. Allows the prototype to overlap colliding tiles up until its center point. This is only respected for character movement and cars driven by players.
 * - `"colliding-with-tiles-only"`: Any prototype with this collision option will only be checked for collision with other prototype's collision masks if they are a tile.
 */
@Serializable(CollisionMaskSerializer::class)
public data class CollisionMask(
    private val _layers: EnumSet<CollisionMaskLayer>,
    public val notCollidingWithItself: Boolean = false,
) {
    public val layers: Set<CollisionMaskLayer> get() = _layers
    // Some of this impl is derived from https://github.com/wube/factorio-data/blob/master/core/lualib/collision-mask-util.lua

    public infix fun collidesWith(other: CollisionMask): Boolean {
        if (this === other) return _layers.isNotEmpty() && !notCollidingWithItself
        if (notCollidingWithItself && other.notCollidingWithItself && _layers == other._layers) return false
        // dear jvm: please optimize away the allocations, thanks
        val intersection = EnumSet.copyOf(_layers)
            .apply { retainAll(other._layers) }
        return intersection.isNotEmpty()
    }

    public companion object {
        public val EMPTY: CollisionMask = CollisionMask(EnumSet.noneOf(CollisionMaskLayer::class.java))

        /** A map of default collision masks for each entity type (used in this library). */
        public val DEFAULT_MASKS: Map<String, CollisionMask>

        /**
         * The default collision mask used by most entities.
         */
        public val PLAIN_OBJECT_MASK: CollisionMask = CollisionMask(
            EnumSet.of(
                CollisionMaskLayer.`item-layer`,
                CollisionMaskLayer.`object-layer`,
                CollisionMaskLayer.`player-layer`,
                CollisionMaskLayer.`water-tile`
            )
        )


        init {
            val sets = arrayOf(
                PLAIN_OBJECT_MASK,
                CollisionMask(EnumSet.of(CollisionMaskLayer.`train-layer`)),
                CollisionMask(
                    EnumSet.of(
                        CollisionMaskLayer.`floor-layer`,
                        CollisionMaskLayer.`item-layer`,
                        CollisionMaskLayer.`object-layer`,
                        CollisionMaskLayer.`rail-layer`,
                        CollisionMaskLayer.`water-tile`
                    ),
                    notCollidingWithItself = true
                ),
                CollisionMask(
                    EnumSet.of(
                        CollisionMaskLayer.`item-layer`,
                        CollisionMaskLayer.`object-layer`,
                        CollisionMaskLayer.`player-layer`,
                        CollisionMaskLayer.`train-layer`,
                        CollisionMaskLayer.`water-tile`
                    )
                ),
                CollisionMask(
                    EnumSet.of(
                        CollisionMaskLayer.`floor-layer`,
                        CollisionMaskLayer.`object-layer`,
                        CollisionMaskLayer.`water-tile`
                    )
                ),
                CollisionMask(
                    EnumSet.of(
                        CollisionMaskLayer.`object-layer`,
                        CollisionMaskLayer.`rail-layer`,
                        CollisionMaskLayer.`water-tile`
                    )
                ),
                CollisionMask(
                    EnumSet.of(
                        CollisionMaskLayer.`item-layer`,
                        CollisionMaskLayer.`object-layer`,
                        CollisionMaskLayer.`transport-belt-layer`,
                        CollisionMaskLayer.`water-tile`
                    )
                ),
                CollisionMask(
                    EnumSet.of(
                        CollisionMaskLayer.`floor-layer`,
                        CollisionMaskLayer.`item-layer`,
                        CollisionMaskLayer.`rail-layer`
                    )
                ),
                CollisionMask(
                    EnumSet.of(
                        CollisionMaskLayer.`floor-layer`,
                        CollisionMaskLayer.`object-layer`,
                        CollisionMaskLayer.`transport-belt-layer`,
                        CollisionMaskLayer.`water-tile`
                    )
                ),
            )
            DEFAULT_MASKS = mapOf(
                "accumulator" to sets[0],
                "ammo-turret" to sets[0],
                "arithmetic-combinator" to sets[0],
                "artillery-turret" to sets[0],
                "artillery-wagon" to sets[1],
                "assembling-machine" to sets[0],
                "beacon" to sets[0],
                "boiler" to sets[0],
                "burner-generator" to sets[0],
                "cargo-wagon" to sets[1],
                "constant-combinator" to sets[0],
                "container" to sets[0],
                "curved-rail" to sets[2],
                "decider-combinator" to sets[0],
                "electric-energy-interface" to sets[0],
                "electric-pole" to sets[0],
                "electric-turret" to sets[0],
                "fluid-turret" to sets[0],
                "fluid-wagon" to sets[1],
                "furnace" to sets[0],
                "gate" to sets[3],
                "generator" to sets[0],
                "heat-interface" to sets[0],
                "heat-pipe" to sets[4],
                "infinity-container" to sets[0],
                "infinity-pipe" to sets[0],
                "inserter" to sets[0],
                "lab" to sets[0],
                "lamp" to sets[0],
                "land-mine" to sets[5],
                "linked-belt" to sets[6],
                "linked-container" to sets[0],
                "loader-1x1" to sets[6],
                "loader" to sets[6],
                "locomotive" to sets[1],
                "logistic-container" to sets[0],
                "mining-drill" to sets[0],
                "offshore-pump" to sets[0],
                "pipe-to-ground" to sets[0],
                "pipe" to sets[0],
                "player-port" to sets[4],
                "power-switch" to sets[0],
                "programmable-speaker" to sets[0],
                "pump" to sets[0],
                "radar" to sets[0],
                "rail-chain-signal" to sets[7],
                "rail-signal" to sets[7],
                "reactor" to sets[0],
                "roboport" to sets[0],
                "rocket-silo" to sets[0],
                "simple-entity-with-force" to sets[0],
                "simple-entity-with-owner" to sets[0],
                "solar-panel" to sets[0],
                "splitter" to sets[6],
                "storage-tank" to sets[0],
                "straight-rail" to sets[2],
                "train-stop" to sets[0],
                "transport-belt" to sets[8],
                "turret" to sets[0],
                "underground-belt" to sets[6],
                "wall" to sets[0],
            )
        }
    }
}

private val stringListSerializer = LuaListSerializer(String.serializer())

internal object CollisionMaskSerializer : KSerializer<CollisionMask> {
    private val layersByName = CollisionMaskLayer.entries.associateBy { it.name }

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        SerialDescriptor(CollisionMask::class.qualifiedName!!, ListSerializer(String.serializer()).descriptor)

    override fun deserialize(decoder: Decoder): CollisionMask {
        val options = decoder.decodeSerializableValue(stringListSerializer)

        val layers = EnumSet.noneOf(CollisionMaskLayer::class.java)
        var notCollidingWithItself = false
        for (option in options) {
            if (option == "not-colliding-with-itself") notCollidingWithItself = true
            else {
                val layer = layersByName[option]
                if (layer != null) layers.add(layer)
            }
        }
        return CollisionMask(layers, notCollidingWithItself)
    }

    override fun serialize(encoder: Encoder, value: CollisionMask) {
        val options = mutableListOf<String>()
        options.addAll(value.layers.map { it.name })
        if (value.notCollidingWithItself) options.add("not-colliding-with-itself")
        encoder.encodeSerializableValue(stringListSerializer, options)
    }
}
