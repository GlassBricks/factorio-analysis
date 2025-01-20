package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.RecipeID
import glassbricks.recipeanalysis.*

data class ResearchConfig(
    val maxQuality: Quality? = null,
    val recipeProductivity: Map<RecipeID, Float> = emptyMap(),
    val miningProductivity: Float = 0f,
)

data class MachineSetup<M : AnyMachine<*>>(
    val machine: M,
    val process: RecipeOrResource<M>,
    val maxQuality: Quality? = null,
    val extraProductivity: Float = 0f,
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
            is Recipe -> config.recipeProductivity[RecipeID(process.prototype.name)] ?: 0f
            is Resource -> config.miningProductivity
        },
    )

    init {
        require(machine.canProcess(process)) { "$machine does not accept $process" }
    }

    val effectsUsed: IntEffects = machine.effects.let {
        if (extraProductivity != 0f) it + IntEffects(productivity = extraProductivity.toIntEffect()) else it
    }
    val cycleTime: Time = process.craftingTime / machine.finalCraftingSpeed
    val cycleOutputs = process.outputs.applyProdAndQuality(
        effectsUsed,
        process.outputsToIgnoreProductivity,
        process.inputQuality,
        maxQuality,
    )
    val cycleInputs get() = process.inputs
    override val netRate: IngredientRate = (cycleOutputs - cycleInputs) / cycleTime

    override fun toString(): String = "(${machine} -> ${process})"
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

typealias CraftingSetup = MachineSetup<AnyCraftingMachine>
typealias MiningSetup = MachineSetup<AnyMiningDrill>

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
