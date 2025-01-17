package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.*
import kotlin.collections.set

class RecipePrototypes(val dataRaw: DataRaw) : IngredientsMap {
    val qualities = dataRaw.quality.values
        .filter { it.name != "quality-unknown" }
        .sortedBy { it.level.toInt() }

    init {
        for ((prevQ, nextQ) in qualities.zipWithNext()) {
            check(prevQ.next?.value == nextQ.name) { "Quality levels are not contiguous: $prevQ -> $nextQ" }
        }
    }

    val defaultQuality get() = qualities.first()

    val items: Map<String, Item> = dataRaw.allItemPrototypes().associate { it.name to getItem(it) }
    val fluids: Map<String, Fluid> = dataRaw.fluid.mapValues { BasicFluid(it.value) }
    override val ingredients: Map<String, RealIngredient> = items + fluids

    val modules: Map<String, Module> = dataRaw.module.mapValues { Module(it.value) }

    val builtByMap: Map<EntityID, Item> = buildMap {
        for (item in items.values) {
            val placeResult = item.prototype.place_result
            if (placeResult.isBlank()) continue
            if (placeResult in this && item.prototype.flags?.contains(ItemFlag.`primary-place-result`) != true) {
                continue
            }
            this[placeResult] = item
        }
    }

    fun itemToBuild(entity: EntityID): Item? = builtByMap[entity]

    val beacons: Map<String, Beacon> = dataRaw.beacon.mapValues { Beacon(it.value) }
    val beacon get() = beacons.values.first()

    val machines: Map<String, Machine> = dataRaw.allCraftingMachinePrototypes().associate { it.name to Machine(it) }

    val recipes: Map<String, Recipe> = dataRaw.recipe.mapValues { Recipe.fromPrototype(it.value, this) }
}

val SpaceAge by lazy { RecipePrototypes(SpaceAgeDataRaw) }
