package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.*
import glassbricks.recipeanalysis.Ingredient

class FactorioPrototypes(dataRaw: DataRaw) : FactorioPrototypesScope, IngredientsMap {
    override val prototypes: FactorioPrototypes get() = this

    val qualityMap = loadQualities(dataRaw.quality)
    val qualities = qualityMap.values.sorted()
    override val defaultQuality get() = qualities.first()

    val items: Map<String, Item> =
        dataRaw.allItemPrototypes().associate { it.name to getItem(it, defaultQuality) }
    val fluids: Map<String, Fluid> =
        dataRaw.fluid.values.associate { it.name to Fluid(it) }

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
    val modules: Map<String, Module> = items.mapValuesNotNull { it.value as? Module }

    val craftingMachines: Map<String, CraftingMachine> =
        dataRaw.allCraftingMachinePrototypes().associate { it.name to CraftingMachine(it, defaultQuality) }
    val recipes: Map<String, Recipe> =
        dataRaw.recipe.mapValues { Recipe.fromPrototype(it.value, defaultQuality, this) }

    val recipesByCategory = recipes.values.groupBy { it.prototype.category }

    val miningDrills: Map<String, MiningDrill> =
        dataRaw.`mining-drill`.mapValues { MiningDrill(it.value, defaultQuality) }
    val resources: Map<String, Resource> =
        dataRaw.resource.mapValues { Resource.fromPrototype(it.value, this) }

    val equipment = dataRaw.allEquipmentPrototypes().associateBy { it.name }

    override fun get(itemID: ItemID): Item = items.getValue(itemID.value)
    override fun get(fluidID: FluidID): Fluid = fluids.getValue(fluidID.value)

    override fun toString(): String = "FactorioPrototypes"
}

interface FactorioPrototypesScope {
    val prototypes: FactorioPrototypes

    fun quality(name: String): Quality = prototypes.qualityMap[name] ?: error("Quality $name not found")

    fun item(name: String): Item = prototypes.items.getValue(name)
    fun fluid(name: String): Fluid = prototypes.fluids.getValue(name)

    fun module(name: String): Module = prototypes.modules.getValue(name)
    val beacon: Beacon get() = prototypes.beacons.values.first()

    fun craftingMachine(name: String): CraftingMachine = prototypes.craftingMachines.getValue(name)
    fun recipe(name: String): Recipe = prototypes.recipes.getValue(name)
    fun miningDrill(name: String): MiningDrill = prototypes.miningDrills.getValue(name)
    fun resource(name: String): Resource = prototypes.resources.getValue(name)

    fun machine(name: String): BaseMachine<*> = prototypes.craftingMachines[name]
        ?: prototypes.miningDrills[name]
        ?: error("Machine $name not found")

    fun itemOfOrNull(entity: EntityPrototype): Item? = prototypes.builtByMap[entity.name]
    fun itemOfOrNull(entity: Entity): Item? = itemOfOrNull(entity.prototype)?.withQuality(entity.quality)
    fun itemOf(entity: Entity): Item = itemOfOrNull(entity) ?: error("Item not found for $entity")
    fun Entity.itemOrNull(): Item? = itemOfOrNull(this)
    fun Entity.item(): Item = itemOrNull() ?: error("Item not found for $this")

    fun recipeOf(ingredient: RealIngredient): Recipe = when (ingredient) {
        is Item -> recipe(ingredient.prototype.name).withQuality(ingredient.quality)
        is Fluid -> recipe(ingredient.prototype.name)
    }

    fun RealIngredient.recipe(): Recipe = recipeOf(this)

}

fun Ingredient.maybeWithQuality(quality: Quality): Ingredient = when (this) {
    is Item -> this.withQuality(quality)
    else -> this
}

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
