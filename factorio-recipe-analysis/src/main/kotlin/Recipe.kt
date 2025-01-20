package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.EffectType
import glassbricks.factorio.prototypes.RecipePrototype
import glassbricks.recipeanalysis.*
import java.util.*

sealed interface RecipeOrResource<M : AnyMachine<*>> {
    val inputs: IngredientVector
    val outputs: IngredientVector
    val outputsToIgnoreProductivity: IngredientVector
    val craftingTime: Time

    val inputQuality: Quality
    fun withQualityOrNull(quality: Quality): RecipeOrResource<M>?
}

class Recipe private constructor(
    val prototype: RecipePrototype,
    override val inputQuality: Quality,
    val baseIngredients: IngredientVector,
    val baseProducts: IngredientVector,
    val baseProductsIgnoreProd: IngredientVector,
    private val allowedModuleEffects: EnumSet<EffectType>,
) : RecipeOrResource<AnyCraftingMachine> {
    override val craftingTime: Time get() = Time(prototype.energy_required)
    override val inputs get() = baseIngredients.withItemsQuality(inputQuality)
    override val outputs get() = baseProducts.withItemsQuality(inputQuality)
    override val outputsToIgnoreProductivity get() = baseProductsIgnoreProd.withItemsQuality(inputQuality)

    override fun withQualityOrNull(quality: Quality): Recipe? {
        if (quality == this.inputQuality) return this
        if (quality.level != 0 && baseIngredients.keys.none { it is Item }) return null
        return Recipe(
            prototype = prototype,
            inputQuality = quality,
            baseIngredients = baseIngredients,
            baseProducts = baseProducts,
            baseProductsIgnoreProd = baseProductsIgnoreProd,
            allowedModuleEffects = allowedModuleEffects
        )
    }

    fun withQuality(quality: Quality): Recipe = withQualityOrNull(quality)
        ?: throw IllegalArgumentException("Cannot change quality of recipe $this to $quality")

    fun acceptsModule(module: Module): Boolean {
        if (prototype.allowed_module_categories?.let { module.prototype.category in it } == false) return false
        if (!allowedModuleEffects.containsAll(module.usedPositiveEffects)) return false
        return true
    }

    fun acceptsModules(modules: Iterable<Module>): Boolean = modules.all { this.acceptsModule(it) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Recipe) return false

        if (prototype != other.prototype) return false
        if (inputQuality != other.inputQuality) return false
        return true
    }

    override fun hashCode(): Int {
        var result = prototype.hashCode()
        result = 31 * result + inputQuality.hashCode()
        return result
    }

    override fun toString(): String = if (inputQuality.level == 0) prototype.name
    else "${prototype.name}(${inputQuality.prototype.name})"

    companion object {
        fun fromPrototype(
            prototype: RecipePrototype,
            quality: Quality,
            map: IngredientsMap,
        ): Recipe {
            val ingredients = buildMap {
                for (ingredient in prototype.ingredients.orEmpty()) {
                    val ingredientAmount = map.getIngredientAmount(ingredient)
                    put(ingredientAmount.ingredient, ingredientAmount.amount)
                }
            }
            val (products, ignoreFromProductivity) = map.getProductsVector(prototype.results)
            val allowedModuleEffects = EnumSet.noneOf(EffectType::class.java)
            if (prototype.allow_consumption) allowedModuleEffects.add(EffectType.consumption)
            if (prototype.allow_speed) allowedModuleEffects.add(EffectType.speed)
            if (prototype.allow_productivity) allowedModuleEffects.add(EffectType.productivity)
            if (prototype.allow_pollution) allowedModuleEffects.add(EffectType.pollution)
            if (prototype.allow_quality) allowedModuleEffects.add(EffectType.quality)

            return Recipe(
                prototype = prototype,
                inputQuality = quality,
                baseIngredients = vector(ingredients),
                baseProducts = products,
                baseProductsIgnoreProd = vector(ignoreFromProductivity),
                allowedModuleEffects = allowedModuleEffects
            )
        }
    }
}

private inline fun IngredientVector.vectorMapKeys(transform: (Ingredient) -> Ingredient): IngredientVector =
    vectorUnsafe(this.mapKeys { transform(it.key) })

fun Ingredient.maybeWithQuality(quality: Quality): Ingredient = when (this) {
    is Item -> this.withQuality(quality)
    else -> this
}

internal fun IngredientVector.withItemsQuality(quality: Quality): IngredientVector =
    if (quality.level == 0) this else
        vectorMapKeys { it.maybeWithQuality(quality) }
