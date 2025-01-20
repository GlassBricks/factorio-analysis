package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.*

class FactorioPrototypes(dataRaw: DataRaw) : IngredientsMap, WithFactorioPrototypes {
    override val prototypes: FactorioPrototypes get() = this

    val qualityMap = loadQualities(dataRaw.quality)
    val qualities = qualityMap.values.sorted()
    override val defaultQuality get() = qualities.first()

    override val items: Map<ItemID, Item> =
        dataRaw.allItemPrototypes().associate { ItemID(it.name) to getItem(it, defaultQuality) }
    override val fluids: Map<FluidID, Fluid> =
        dataRaw.fluid.values.associate { FluidID(it.name) to Fluid(it) }

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
    val modules: Map<ItemID, Module> = items.mapValuesNotNull { it.value as? Module }

    val craftingMachines: Map<String, CraftingMachine> =
        dataRaw.allCraftingMachinePrototypes().associate { it.name to CraftingMachine(it, defaultQuality) }
    val recipes: Map<String, Recipe> =
        dataRaw.recipe.mapValues { Recipe.fromPrototype(it.value, defaultQuality, this) }

    val miningDrills: Map<String, MiningDrill> =
        dataRaw.`mining-drill`.mapValues { MiningDrill(it.value, defaultQuality) }
    val resources: Map<String, Resource> =
        dataRaw.resource.mapValues { Resource.fromPrototype(it.value, this) }

    val recipesByCategory = recipes.values.groupBy { it.prototype.category }
}

interface WithFactorioPrototypes {
    val prototypes: FactorioPrototypes

    fun quality(name: String): Quality = prototypes.qualityMap[name] ?: error("Quality $name not found")

    fun item(name: String): Item = prototypes.items.getValue(ItemID(name))
    fun fluid(name: String): Fluid = prototypes.fluids.getValue(FluidID(name))

    fun module(name: String): Module = prototypes.modules.getValue(ItemID(name))
    val beacon: Beacon get() = prototypes.beacons.values.first()

    fun craftingMachine(name: String): CraftingMachine = prototypes.craftingMachines.getValue(name)
    fun recipe(name: String): Recipe = prototypes.recipes.getValue(name)
    fun miningDrill(name: String): MiningDrill = prototypes.miningDrills.getValue(name)
    fun resource(name: String): Resource = prototypes.resources.getValue(name)

    fun itemOfOrNull(entity: EntityPrototype): Item? = prototypes.builtByMap[entity.name]
    fun itemOfOrNull(entity: Entity): Item? = itemOfOrNull(entity.prototype)?.withQuality(entity.quality)
    fun Entity.itemOrNull(): Item? = itemOfOrNull(this)

    fun itemOf(entity: EntityPrototype): Item = itemOfOrNull(entity) ?: error("Item not found for $entity")
    fun itemOf(entity: Entity): Item = itemOfOrNull(entity) ?: error("Item not found for $entity")
    fun Entity.item(): Item = itemOrNull() ?: error("Item not found for $this")
}

val WithFactorioPrototypes.qualities get() = prototypes.qualities

val SpaceAge by lazy { FactorioPrototypes(SpaceAgeDataRaw) }

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
