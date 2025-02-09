package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.ResourceEntityPrototype
import glassbricks.recipeanalysis.*

class Resource private constructor(
    override val prototype: ResourceEntityPrototype,
    override val inputs: Vector<Ingredient>,
    override val outputs: Vector<Ingredient>,
    override val outputsToIgnoreProductivity: Vector<Ingredient>,
    override val inputQuality: Quality,
) : RecipeOrResource<AnyMiningDrill> {
    override val craftingTime: Time
        get() = Time(prototype.minable!!.mining_time)

    override fun withQualityOrNull(quality: Quality): RecipeOrResource<AnyMiningDrill>? =
        if (quality == this.inputQuality) this else null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Resource) return false

        return prototype == other.prototype
    }

    override fun hashCode(): Int = prototype.hashCode()

    override fun toString(): String = prototype.name

    companion object {
        fun fromPrototype(prototype: ResourceEntityPrototype, ingredientsMap: IngredientsMap): Resource {
            val minable = requireNotNull(prototype.minable) { "Resource $prototype has no minable" }
            val inputs = minable.required_fluid?.let { fluidId ->
                val fluid = ingredientsMap.get(fluidId)
                vector<Ingredient>(fluid to minable.fluid_amount)
            } ?: emptyVector()
            val (products, prod) = minable.results?.let { products ->
                ingredientsMap.getProductsVector(products)
            } ?: run {
                val item = ingredientsMap.get(minable.result!!)
                vector<Ingredient>(item to minable.count.toDouble()) to emptyVector()
            }
            return Resource(
                prototype = prototype,
                inputs = inputs,
                outputs = products,
                outputsToIgnoreProductivity = prod,
                inputQuality = ingredientsMap.defaultQuality
            )
        }
    }

}
