package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.*
import kotlin.collections.set

private inline fun <K, V, R> Map<K, V>.mapValuesNotNull(transform: (Map.Entry<K, V>) -> R?): Map<K, R> {
    val destination = LinkedHashMap<K, R>()
    for (entry in entries) {
        val newValue = transform(entry)
        if (newValue != null) {
            destination[entry.key] = newValue
        }
    }
    return destination
}

interface WithPrototypes {
    val prototypes: FactorioPrototypes
}

class FactorioPrototypes(dataRaw: DataRaw) : IngredientsMap, WithPrototypes {
    override val prototypes: FactorioPrototypes get() = this
    val qualitiesMap = loadQualities(dataRaw.quality)
    val qualities = qualitiesMap.values.sorted()
    val defaultQuality get() = qualities.first()

    val items: Map<String, Item> = dataRaw.allItemPrototypes().associate { it.name to getItem(it, defaultQuality) }
    val fluids: Map<String, Fluid> = dataRaw.fluid.mapValues { Fluid(it.value) }
    override val ingredients: Map<String, RealIngredient> = items + fluids

    val modules: Map<String, Module> = items.mapValuesNotNull { it.value as? Module }

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

    fun itemToBuild(entity: EntityPrototype): Item? = builtByMap[entity.name]
    fun itemToBuild(entity: Entity): Item? = builtByMap[entity.prototype.name]?.withQuality(entity.quality)

    val beacons: Map<String, Beacon> = dataRaw.beacon.mapValues { Beacon(it.value, defaultQuality) }

    val craftingMachines: Map<String, CraftingMachine> =
        dataRaw.allCraftingMachinePrototypes().associate { it.name to CraftingMachine(it, defaultQuality) }

    val recipes: Map<String, Recipe> =
        dataRaw.recipe.mapValues { Recipe.fromPrototype(it.value, defaultQuality, this) }

    fun quality(name: String): Quality = qualitiesMap[name] ?: error("Quality $name not found")
    fun item(name: String): Item = items.getValue(name)
    fun fluid(name: String): Fluid = fluids.getValue(name)
    fun ingredient(name: String): RealIngredient = ingredients.getValue(name)
    fun module(name: String): Module = modules.getValue(name)
    val beacon: Beacon get() = beacons.values.first()
    fun craftingMachine(name: String): CraftingMachine = craftingMachines.getValue(name)
    fun recipe(name: String): Recipe = recipes.getValue(name)
}

val SpaceAge by lazy { FactorioPrototypes(SpaceAgeDataRaw) }
