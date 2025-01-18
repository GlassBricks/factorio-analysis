package glassbricks.factorio.recipes

import glassbricks.recipeanalysis.*

data class ResearchConfig(
    val maxQuality: Quality? = null,
    val recipeProductivity: Map<String, Float> = emptyMap(),
)

data class CraftingProcess(
    val machine: AnyCraftingMachine,
    val recipe: Recipe,
    val maxQuality: Quality? = null,
    val extraProductivity: Float = 0f,
) : LpProcess {
    constructor(
        machine: AnyCraftingMachine,
        recipe: Recipe,
        config: ResearchConfig,
    ) : this(
        machine,
        recipe,
        config.maxQuality,
        config.recipeProductivity[recipe.prototype.name] ?: 0f,
    )

    init {
        require(machine.acceptsRecipe(recipe)) { "$machine does not accept $recipe" }
    }

    val effectsUsed: IntEffects = machine.effects.let {
        if (extraProductivity != 0f) it + IntEffects(productivity = extraProductivity.toIntEffect()) else it
    }
    val cycleTime: Time = recipe.craftingTime / machine.finalCraftingSpeed
    val cycleOutputs = recipe.outputs.applyProdAndQuality(
        effectsUsed,
        recipe.outputsToIgnoreProductivity,
        recipe.quality,
        maxQuality,
    )
    val cycleInputs get() = recipe.inputs
    override val netRate: IngredientRate = (cycleOutputs - cycleInputs) / cycleTime

    override fun toString(): String = "(${machine} -> ${recipe})"

    init {
        require(machine.acceptsRecipe(recipe)) { "Machine ${machine.prototype.name} does not accept recipe ${recipe.prototype.name}" }
    }
}

fun AnyCraftingMachine.crafting(recipe: Recipe, config: ResearchConfig = ResearchConfig()) =
    CraftingProcess(this, recipe, config)

fun AnyCraftingMachine.craftingOrNull(recipe: Recipe, config: ResearchConfig = ResearchConfig()): CraftingProcess? =
    if (!this.acceptsRecipe(recipe)) null
    else CraftingProcess(this, recipe, config)

fun IngredientVector.applyProductivity(
    productsIgnoredFromProductivity: IngredientVector?,
    multiplier: Float,
): IngredientVector {
    if (multiplier == 1f) return this
    val productsToMultiply =
        if (productsIgnoredFromProductivity != null) this - productsIgnoredFromProductivity else this

    return (productsToMultiply * multiplier.toDouble())
        .let { if (productsIgnoredFromProductivity != null) it + productsIgnoredFromProductivity else it }
}

fun IngredientVector.applyQualityRolling(
    startingQuality: Quality,
    finalQuality: Quality?,
    qualityChance: Float,
): IngredientVector {
    if (qualityChance > 1) TODO("Quality chance > 100%")
    var result: IngredientVector = vector()
    var curQuality = startingQuality
    var propRemaining = 1.0
    while (curQuality != finalQuality && curQuality.nextQuality != null) {
        val probQualityIncrease = if (curQuality == startingQuality) qualityChance else .1f
        val propCurrent = propRemaining * (1 - probQualityIncrease)
        result += propCurrent * this.withItemsQuality(curQuality)
        propRemaining *= probQualityIncrease
        curQuality = curQuality.nextQuality!!
    }
    result += propRemaining * this.withItemsQuality(curQuality)
    return result
}

fun IngredientVector.applyProdAndQuality(
    effects: IntEffects,
    productsIgnoredFromProductivity: IngredientVector?,
    startingQuality: Quality,
    finalQuality: Quality?,
): IngredientVector = applyProductivity(
    productsIgnoredFromProductivity,
    effects.prodMultiplier,
).applyQualityRolling(
    startingQuality,
    finalQuality,
    effects.qualityChance,
)
