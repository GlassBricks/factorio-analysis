package glassbricks.factorio.recipes

import glassbricks.recipeanalysis.*
import glassbricks.recipeanalysis.CraftingProcess

data class ResearchConfig(
    val maxQuality: Quality? = null,
    val recipeProductivity: Map<String, Float> = emptyMap(),
)

class CraftingSetup(
    val machine: AnyCraftingMachine,
    val recipe: CraftingRecipe,
    config: ResearchConfig,
) : CraftingProcess {
    init {
        for (module in machine.modulesUsed) {
            require(recipe.acceptsModule(module)) {
                "Module ${module.prototype.name} is not allowed for recipe ${recipe.prototype.name}"
            }
        }
    }

    val effectsUsed: IntEffects = machine.effects.let {
        val additionalProd = config.recipeProductivity[recipe.prototype.name]
        if (additionalProd != null) it + IntEffects(productivity = additionalProd.toIntEffect())
        else it
    }
    val cycleTime: Time = recipe.craftingTime / machine.finalCraftingSpeed
    val cycleOutputs = recipe.outputs.applyProdAndQuality(
        effectsUsed,
        recipe.outputsToIgnoreProductivity,
        recipe.quality,
        config.maxQuality,
    )
    val cycleInputs get() = recipe.inputs
    override val netRate: IngredientRate = (cycleOutputs - cycleInputs) / cycleTime

    override fun toString(): String {
        // todo: better toString
        return "CraftingSetup(machine=${machine.prototype.name}, recipe=${recipe.prototype.name})"
    }

    init {
        require(machine.acceptsRecipe(recipe)) { "Machine ${machine.prototype.name} does not accept recipe ${recipe.prototype.name}" }
    }
}

fun AnyCraftingMachine.crafting(recipe: CraftingRecipe, config: ResearchConfig = ResearchConfig()) =
    CraftingSetup(this, recipe, config)

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
