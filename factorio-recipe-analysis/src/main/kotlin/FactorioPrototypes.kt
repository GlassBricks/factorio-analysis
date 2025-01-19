package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.*

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

class FactorioPrototypes(dataRaw: DataRaw) : IngredientsMap, WithFactorioPrototypes {
    override val prototypes: FactorioPrototypes get() = this
    val qualityMap = loadQualities(dataRaw.quality)
    val qualities = qualityMap.values.sorted()
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

    val beacons: Map<String, Beacon> = dataRaw.beacon.mapValues { Beacon(it.value, defaultQuality) }

    val craftingMachines: Map<String, CraftingMachine> =
        dataRaw.allCraftingMachinePrototypes().associate { it.name to CraftingMachine(it, defaultQuality) }

    val recipes: Map<String, Recipe> =
        dataRaw.recipe.mapValues { Recipe.fromPrototype(it.value, defaultQuality, this) }

    val recipesByCategory = recipes.values.groupBy { it.prototype.category }
}

interface WithFactorioPrototypes {
    val prototypes: FactorioPrototypes

    fun quality(name: String): Quality = prototypes.qualityMap[name] ?: error("Quality $name not found")
    fun item(name: String): Item = prototypes.items.getValue(name)
    fun fluid(name: String): Fluid = prototypes.fluids.getValue(name)
    fun ingredient(name: String): RealIngredient = prototypes.ingredients.getValue(name)
    fun module(name: String): Module = prototypes.modules.getValue(name)
    fun craftingMachine(name: String): CraftingMachine = prototypes.craftingMachines.getValue(name)
    fun recipe(name: String): Recipe = prototypes.recipes.getValue(name)

    val beacon: Beacon get() = prototypes.beacons.values.first()

    fun itemOfOrNull(entity: EntityPrototype): Item? = prototypes.builtByMap[entity.name]
    fun itemOfOrNull(entity: Entity): Item? = itemOfOrNull(entity.prototype)?.withQuality(entity.quality)
    fun Entity.itemOrNull(): Item? = itemOfOrNull(this)

    fun itemOf(entity: EntityPrototype): Item = itemOfOrNull(entity) ?: error("Item not found for $entity")
    fun itemOf(entity: Entity): Item = itemOfOrNull(entity) ?: error("Item not found for $entity")
    fun Entity.item(): Item = itemOrNull() ?: error("Item not found for $this")
}

val WithFactorioPrototypes.qualities get() = prototypes.qualities

val SpaceAge by lazy { FactorioPrototypes(SpaceAgeDataRaw) }
