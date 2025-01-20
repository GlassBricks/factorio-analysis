package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.RecipeID
import glassbricks.recipeanalysis.*

data class ResearchConfig(
    val maxQuality: Quality? = null,
    val recipeProductivity: Map<RecipeID, Float> = emptyMap(),
    val miningProductivity: Float = 0f,
)

data class MachineProcess<M : AnyMachine<*>>(
    val machine: M,
    val recipe: RecipeOrResource<M>,
    val maxQuality: Quality? = null,
    val extraProductivity: Float = 0f,
) : Process {
    constructor(
        machine: M,
        recipe: RecipeOrResource<M>,
        config: ResearchConfig,
    ) : this(
        machine,
        recipe,
        config.maxQuality,
        when (recipe) {
            is Recipe -> config.recipeProductivity[RecipeID(recipe.prototype.name)] ?: 0f
            is Resource -> config.miningProductivity
        },
    )

    init {
        require(machine.canProcess(recipe)) { "$machine does not accept $recipe" }
    }

    val effectsUsed: IntEffects = machine.effects.let {
        if (extraProductivity != 0f) it + IntEffects(productivity = extraProductivity.toIntEffect()) else it
    }
    val cycleTime: Time = recipe.craftingTime / machine.finalCraftingSpeed
    val cycleOutputs = recipe.outputs.applyProdAndQuality(
        effectsUsed,
        recipe.outputsToIgnoreProductivity,
        recipe.inputQuality,
        maxQuality,
    )
    val cycleInputs get() = recipe.inputs
    override val netRate: IngredientRate = (cycleOutputs - cycleInputs) / cycleTime

    override fun toString(): String = "(${machine} -> ${recipe})"
}

fun <M : AnyMachine<*>> M.crafting(
    recipe: RecipeOrResource<M>,
    config: ResearchConfig = ResearchConfig(),
): MachineProcess<M> =
    MachineProcess(this, recipe, config)

fun <M : AnyMachine<*>> M.craftingOrNull(
    recipe: RecipeOrResource<M>,
    config: ResearchConfig = ResearchConfig(),
): MachineProcess<M>? =
    if (!this.canProcess(recipe)) null
    else MachineProcess(this, recipe, config)

typealias CraftingProcess = MachineProcess<AnyCraftingMachine>
typealias MiningProcess = MachineProcess<AnyMiningDrill>

internal fun IngredientVector.applyProductivity(
    productsIgnoredFromProductivity: IngredientVector?,
    multiplier: Float,
): IngredientVector {
    if (multiplier == 1f) return this
    val productsToMultiply =
        if (productsIgnoredFromProductivity != null) this - productsIgnoredFromProductivity else this

    return (productsToMultiply * multiplier.toDouble())
        .let { if (productsIgnoredFromProductivity != null) it + productsIgnoredFromProductivity else it }
}

internal fun IngredientVector.applyQualityRolling(
    startingQuality: Quality,
    finalQuality: Quality?,
    qualityChance: Float,
): IngredientVector {
    if (qualityChance > 1) TODO("Quality chance > 100%")
    var result: IngredientVector = emptyVector()
    var curQuality = startingQuality
    var propRemaining = 1.0
    while (curQuality != finalQuality && curQuality.nextQuality != null) {
        val probQualityIncrease = if (curQuality == startingQuality) qualityChance else .1f
        val propCurrent = propRemaining * (1 - probQualityIncrease)
        result += propCurrent * this.withItemsQuality(curQuality)
        propRemaining *= probQualityIncrease
        curQuality = curQuality.nextQuality
    }
    result += propRemaining * this.withItemsQuality(curQuality)
    return result
}

internal fun IngredientVector.applyProdAndQuality(
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
