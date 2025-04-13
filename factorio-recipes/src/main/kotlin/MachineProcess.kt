package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.RecipeID
import glassbricks.recipeanalysis.*

data class MachineSetup<M : AnyMachine<*>>(val machine: M, val recipe: MachineRecipe<M>) {

    fun toProcess(
        config: ResearchConfig = ResearchConfig(),
        includePowerUsage: Boolean = false,
    ): MachineProcess<M> =
        MachineProcess(this, includePowerUsage = includePowerUsage, researchConfig = config)
}

class MachineProcess<M : AnyMachine<*>>(
    val machine: M,
    val recipe: MachineRecipe<M>,
    val includePowerUsage: Boolean,
    val researchConfig: ResearchConfig = ResearchConfig(),
    skipCanProcessCheck: Boolean = false,
) : Process {
    constructor(
        setup: MachineSetup<M>,
        includePowerUsage: Boolean = false,
        researchConfig: ResearchConfig = ResearchConfig(),
    ) : this(
        setup.machine,
        setup.recipe,
        includePowerUsage,
        researchConfig
    )

    init {
        if (!skipCanProcessCheck) {
            require(machine.canProcess(recipe)) { "$machine does not accept $recipe" }
        }
    }

    val setup get() = MachineSetup(machine, recipe)

    private val effectsUsed: IntEffects
        get() {
            val extraProductivity = when (recipe) {
                is Recipe -> researchConfig.recipeProductivity[RecipeID(recipe.prototype.name)] ?: 0.0
                is Resource -> researchConfig.miningProductivity
            }
            return machine.effects.let {
                if (extraProductivity != 0.0) it + IntEffects(
                    productivity = extraProductivity.toFloat().toIntEffect()
                ) else it
            }
        }

    val cycleTime: Time get() = recipe.craftingTime / machine.finalCraftingSpeed
    val cycleOutputs
        get() = recipe.outputs.applyProdAndQuality(
            effectsUsed,
            recipe.outputsToIgnoreProductivity,
            recipe.inputQuality,
            researchConfig.maxQuality,
        )
    val cycleInputs get() = recipe.inputs

    override val netRate: IngredientRate = buildVector {
        this += cycleOutputs
        this -= cycleInputs
        this /= cycleTime.seconds
        if (includePowerUsage) {
            machine.powerType?.let {
                inc(it, -machine.powerUsage)
            }
        }
    }.castUnits()

    override fun toString(): String = "$machine: $recipe"
}

fun <M : AnyMachine<*>> M.crafting(process: MachineRecipe<M>): MachineSetup<M> =
    MachineSetup(this, process)

fun <M : AnyMachine<*>> M.craftingOrNull(process: MachineRecipe<M>): MachineSetup<M>? =
    if (!this.canProcess(process)) null
    else MachineSetup(this, process)

internal fun Vector<Ingredient>.applyProductivity(
    productsIgnoredFromProductivity: Vector<Ingredient>,
    multiplier: Float,
): Vector<Ingredient> {
    return (this - productsIgnoredFromProductivity) * multiplier.toDouble() + productsIgnoredFromProductivity
}

internal fun Vector<Ingredient>.applyQualityRolling(
    startingQuality: Quality,
    finalQuality: Quality?,
    qualityChance: Float,
): Vector<Ingredient> {
    if (qualityChance == 0f) return this
    if (qualityChance > 1) TODO("Quality chance > 100%")
    return buildVector {
        var curQuality = startingQuality
        var propRemaining = 1.0
        while (curQuality != finalQuality && curQuality.nextQuality != null) {
            val probQualityIncrease = if (curQuality == startingQuality) qualityChance else .1f
            val propCurrent = propRemaining * (1 - probQualityIncrease)
            this.addMul(this@applyQualityRolling.withItemsQuality(curQuality), propCurrent)
            propRemaining *= probQualityIncrease
            curQuality = curQuality.nextQuality
        }
        this.addMul(this@applyQualityRolling.withItemsQuality(curQuality), propRemaining)
    }
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
