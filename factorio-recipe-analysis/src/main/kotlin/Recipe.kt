package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.EffectType
import glassbricks.factorio.prototypes.RecipePrototype
import glassbricks.recipeanalysis.*
import java.util.*

/**
 * Represents multiple "processes" that require a machine to run
 * - Crafting recipes
 * - Mining drills, pumpjacks
 * - Offshore pumping
 * Possibly others.
 */
sealed interface RecipeOrProduction {
    val inputs: IngredientVector
    val outputs: IngredientVector
    val outputsToIgnoreProductivity: IngredientVector?
    val craftingTime: Time
}

class Recipe private constructor(
    val prototype: RecipePrototype,
    val quality: Quality,
    val baseIngredients: IngredientVector,
    val baseProducts: IngredientVector,
    val baseProductsIgnoreProd: IngredientVector?,
    private val allowedModuleEffects: EnumSet<EffectType>,
) : RecipeOrProduction {
    override val craftingTime: Time get() = Time(prototype.energy_required)
    override val inputs get() = baseIngredients.withItemsQuality(quality)
    override val outputs get() = baseProducts.withItemsQuality(quality)
    override val outputsToIgnoreProductivity get() = baseProductsIgnoreProd?.withItemsQuality(quality)

    fun withQualityOrNull(quality: Quality): Recipe? {
        if (quality == this.quality) return this
        if (quality.level != 0 && baseIngredients.keys.none { it is Item }) return null
        return Recipe(
            prototype = prototype,
            quality = quality,
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Recipe) return false

        if (prototype != other.prototype) return false
        if (quality != other.quality) return false
        return true
    }

    override fun hashCode(): Int {
        var result = prototype.hashCode()
        result = 31 * result + quality.hashCode()
        return result
    }

    override fun toString(): String = if (quality.level == 0) prototype.name
    else "${prototype.name}(${quality.prototype.name})"

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
            val ignoreFromProductivity = mutableMapOf<Ingredient, Double>()
            val products = buildMap {
                for (product in prototype.results.orEmpty()) {
                    val productAmount = map.getProductAmount(product)
                    put(productAmount.ingredient, productAmount.amount)
                    ignoreFromProductivity[productAmount.ingredient] =
                        minOf(productAmount.ignoredByProductivityAmount, productAmount.amount)
                }
            }
            val allowedModuleEffects = EnumSet.noneOf(EffectType::class.java)
            if (prototype.allow_consumption) allowedModuleEffects.add(EffectType.consumption)
            if (prototype.allow_speed) allowedModuleEffects.add(EffectType.speed)
            if (prototype.allow_productivity) allowedModuleEffects.add(EffectType.productivity)
            if (prototype.allow_pollution) allowedModuleEffects.add(EffectType.pollution)
            if (prototype.allow_quality) allowedModuleEffects.add(EffectType.quality)

            return Recipe(
                prototype = prototype,
                quality = quality,
                baseIngredients = vector(ingredients),
                baseProducts = vector(products),
                baseProductsIgnoreProd = if (ignoreFromProductivity.isEmpty()) null else vector(ignoreFromProductivity),
                allowedModuleEffects = allowedModuleEffects
            )
        }
    }
}

fun Recipe.acceptsModules(modules: Iterable<Module>): Boolean = modules.all { this.acceptsModule(it) }

private inline fun IngredientVector.vectorMapKeys(transform: (Ingredient) -> Ingredient): IngredientVector =
    vectorUnsafe(this.mapKeys { transform(it.key) })

fun Ingredient.maybeWithQuality(quality: Quality): Ingredient = when (this) {
    is Item -> this.withQuality(quality)
    else -> this
}

fun IngredientVector.withItemsQuality(quality: Quality): IngredientVector =
    if (quality.level == 0) this else
        vectorMapKeys { it.maybeWithQuality(quality) }
