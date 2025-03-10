package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.EffectType
import glassbricks.factorio.prototypes.Prototype
import glassbricks.factorio.prototypes.RecipePrototype
import glassbricks.recipeanalysis.*
import glassbricks.recipeanalysis.Vector
import java.util.*

sealed interface RecipeOrResource<out M : AnyMachine<*>> {
    val inputs: Vector<Ingredient>
    val outputs: Vector<Ingredient>
    val outputsToIgnoreProductivity: Vector<Ingredient>
    val craftingTime: Time

    val prototype: Prototype
    val craftingCategory: Any

    val inputQuality: Quality
    fun withQualityOrNull(quality: Quality): RecipeOrResource<M>?

    fun acceptsModules(modules: WithModulesUsed): Boolean
}

class Recipe private constructor(
    override val prototype: RecipePrototype,
    override val inputQuality: Quality,
    val baseIngredients: Vector<Ingredient>,
    val baseProducts: Vector<Ingredient>,
    val baseProductsIgnoreProd: Vector<Ingredient>,
    private val allowedModuleEffects: EnumSet<EffectType>,
) : RecipeOrResource<AnyCraftingMachine> {
    override val craftingTime: Time get() = Time(prototype.energy_required)
    override val inputs = baseIngredients.withItemsQuality(inputQuality)
    override val outputs = baseProducts.withItemsQuality(inputQuality)
    override val outputsToIgnoreProductivity = baseProductsIgnoreProd.withItemsQuality(inputQuality)

    override val craftingCategory: Any get() = prototype.category

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

    override fun acceptsModules(modules: WithModulesUsed): Boolean {
        if (!allowedModuleEffects.containsAll(modules.moduleEffectsUsed)) return false
        prototype.allowed_module_categories?.let { categories ->
            if (!modules.modulesUsed.all { it.prototype.category in categories })
                return false
        }
        return true
    }

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
            val ingredients = buildVector {
                for (ingredient in prototype.ingredients.orEmpty()) {
                    val ingredientAmount = map.getIngredientAmount(ingredient)
                    set(ingredientAmount.ingredient, ingredientAmount.amount)
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
                baseIngredients = ingredients,
                baseProducts = products,
                baseProductsIgnoreProd = ignoreFromProductivity,
                allowedModuleEffects = allowedModuleEffects
            )
        }
    }
}

internal fun Vector<Ingredient>.withItemsQuality(quality: Quality): Vector<Ingredient> =
    if (quality.level == 0) this else mapKeys { it.maybeWithQuality(quality) }
