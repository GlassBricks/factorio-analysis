package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.Prototype
import glassbricks.recipeanalysis.Ingredient
import glassbricks.recipeanalysis.Process
import glassbricks.recipeanalysis.Time
import glassbricks.recipeanalysis.Vector

interface PseudoRecipe {
    val netInputs: Set<Ingredient>
    val netOutputs: Set<Ingredient>
}

sealed interface FactorioPseudoProcess : PseudoRecipe, Process

sealed interface MachineRecipe<out M : AnyMachine<*>> : PseudoRecipe {
    val inputs: Vector<Ingredient>
    val outputs: Vector<Ingredient>
    override val netInputs: Set<Ingredient>
        get() = if (inputs.keys.any { it in outputs.keys }) {
            inputs.keys.filterTo(mutableSetOf()) { inputs[it] > outputs[it] }
        } else {
            inputs.keys
        }

    override val netOutputs: Set<Ingredient>
        get() = if (inputs.keys.any { it in outputs.keys }) {
            outputs.keys.filterTo(mutableSetOf()) { outputs[it] > inputs[it] }
        } else {
            outputs.keys
        }

    val outputsToIgnoreProductivity: Vector<Ingredient>
    val hasFluids: Boolean
    val craftingTime: Time

    val prototype: Prototype
    val craftingCategory: Any

    val inputQuality: Quality
    fun withQualityOrNull(quality: Quality): MachineRecipe<M>?

    fun acceptsModules(modules: WithModulesUsed): Boolean
}
