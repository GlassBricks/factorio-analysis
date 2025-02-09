package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.RecipeID
import glassbricks.recipeanalysis.*

data class ResearchConfig(
    val maxQuality: Quality? = null,
    val recipeProductivity: Map<RecipeID, Double> = emptyMap(),
    val miningProductivity: Double = 0.0,
)

data class MachineSetup<M : AnyMachine<*>>(
    val machine: M,
    val recipe: RecipeOrResource<M>,
    val maxQuality: Quality? = null,
    val extraProductivity: Double = 0.0,
) : Process {
    constructor(
        machine: M,
        process: RecipeOrResource<M>,
        config: ResearchConfig,
    ) : this(
        machine,
        process,
        config.maxQuality,
        when (process) {
            is Recipe -> config.recipeProductivity[RecipeID(process.prototype.name)] ?: 0.0
            is Resource -> config.miningProductivity
        },
    )

    init {
        require(machine.canProcess(recipe)) { "$machine does not accept $recipe" }
    }

    val effectsUsed: IntEffects = machine.effects.let {
        if (extraProductivity != 0.0) it + IntEffects(productivity = extraProductivity.toFloat().toIntEffect()) else it
    }
    val cycleTime: Time = recipe.craftingTime / machine.finalCraftingSpeed
    val cycleOutputs = recipe.outputs.applyProdAndQuality(
        effectsUsed,
        recipe.outputsToIgnoreProductivity,
        recipe.inputQuality,
        maxQuality,
    )
    val cycleInputs get() = recipe.inputs

    override val netRate: IngredientRate = buildVector {
        this += cycleOutputs
        this -= cycleInputs
        this.mapValuesInPlace { it.doubleValue / cycleTime.seconds }
    }.castUnits()

    override fun toString(): String = "$machine --> $recipe"
}

fun <M : AnyMachine<*>> M.processing(
    process: RecipeOrResource<M>,
    config: ResearchConfig = ResearchConfig(),
): MachineSetup<M> =
    MachineSetup(this, process, config)

fun <M : AnyMachine<*>> M.processingOrNull(
    process: RecipeOrResource<M>,
    config: ResearchConfig = ResearchConfig(),
): MachineSetup<M>? =
    if (!this.canProcess(process)) null
    else MachineSetup(this, process, config)

@Suppress("UNCHECKED_CAST")
fun <M : AnyMachine<*>> M.craftingOrNullCast(
    process: RecipeOrResource<*>,
    config: ResearchConfig = ResearchConfig(),
): MachineSetup<M>? =
    if (!this.canProcess(process)) null
    else MachineSetup(this, process as RecipeOrResource<M>, config)

internal fun Vector<Ingredient>.applyProductivity(
    productsIgnoredFromProductivity: Vector<Ingredient>,
    multiplier: Float,
): Vector<Ingredient> {
    // performance: already handles case if productsIgnoredFromProductivity is empty or multiplier is 1.0
    val productsToMultiply = this - productsIgnoredFromProductivity
    return productsToMultiply * multiplier.toDouble() + productsIgnoredFromProductivity
}

internal fun Vector<Ingredient>.applyQualityRolling(
    startingQuality: Quality,
    finalQuality: Quality?,
    qualityChance: Float,
): Vector<Ingredient> {
    if (qualityChance == 0f) return this
    if (qualityChance > 1) TODO("Quality chance > 100%")
    var result: Vector<Ingredient> = emptyVector()
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

internal fun Vector<Ingredient>.applyProdAndQuality(
    effects: IntEffects,
    productsIgnoredFromProductivity: Vector<Ingredient>,
    startingQuality: Quality,
    finalQuality: Quality?,
): Vector<Ingredient> = applyProductivity(
    productsIgnoredFromProductivity,
    effects.prodMultiplier,
).applyQualityRolling(
    startingQuality,
    finalQuality,
    effects.qualityChance,
)
