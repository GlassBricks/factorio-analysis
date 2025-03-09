package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.RecipeID
import glassbricks.recipeanalysis.*

data class ResearchConfig(
    val maxQuality: Quality? = null,
    val recipeProductivity: Map<RecipeID, Double> = emptyMap(),
    val miningProductivity: Double = 0.0,
)

data class MachineSetup<M : AnyMachine<*>>(val machine: M, val recipe: RecipeOrResource<M>) {

    fun toProcess(config: ResearchConfig = ResearchConfig()): MachineProcess<M> =
        MachineProcess(this, config)
}

data class MachineProcess<M : AnyMachine<*>>(
    val machine: M,
    val recipe: RecipeOrResource<M>,
    val researchConfig: ResearchConfig = ResearchConfig(),
) : Process {
    constructor(
        setup: MachineSetup<M>,
        researchConfig: ResearchConfig = ResearchConfig(),
    ) : this(
        setup.machine,
        setup.recipe,
        researchConfig
    )

    init {
        require(machine.canProcess(recipe)) { "$machine does not accept $recipe" }
    }

    val setup get() = MachineSetup(machine, recipe)

    val effectsUsed: IntEffects = run {
        val extraProductivity = when (recipe) {
            is Recipe -> researchConfig.recipeProductivity[RecipeID(recipe.prototype.name)] ?: 0.0
            is Resource -> researchConfig.miningProductivity
        }
        machine.effects.let {
            if (extraProductivity != 0.0) it + IntEffects(
                productivity = extraProductivity.toFloat().toIntEffect()
            ) else it
        }
    }

    val cycleTime: Time = recipe.craftingTime / machine.finalCraftingSpeed
    val cycleOutputs = recipe.outputs.applyProdAndQuality(
        effectsUsed,
        recipe.outputsToIgnoreProductivity,
        recipe.inputQuality,
        researchConfig.maxQuality,
    )
    val cycleInputs get() = recipe.inputs

    override val netRate: IngredientRate = buildVector {
        this += cycleOutputs
        this -= cycleInputs
        this.mapValuesInPlace { it.doubleValue / cycleTime.seconds }
    }.castUnits()

    override fun toString(): String = "$machine: $recipe"
}

fun <M : AnyMachine<*>> M.crafting(process: RecipeOrResource<M>): MachineSetup<M> =
    MachineSetup(this, process)

fun <M : AnyMachine<*>> M.craftingOrNull(process: RecipeOrResource<M>): MachineSetup<M>? =
    if (!this.canProcess(process)) null
    else MachineSetup(this, process)

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
