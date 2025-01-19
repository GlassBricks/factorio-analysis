package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.AssemblingMachinePrototype
import glassbricks.factorio.prototypes.CraftingMachinePrototype

typealias AnyCraftingMachine = AnyMachine<CraftingMachinePrototype>

data class CraftingMachine(
    override val prototype: CraftingMachinePrototype,
    override val quality: Quality,
) : BaseMachine<CraftingMachinePrototype>(), AnyCraftingMachine {
    override val baseCraftingSpeed: Double get() = prototype.crafting_speed * (1.0 + quality.level * 0.3)
    override fun withQuality(quality: Quality): CraftingMachine = CraftingMachine(prototype, quality)
}

typealias CraftingMachineWithModules = MachineWithModules<CraftingMachinePrototype>

fun AnyCraftingMachine.acceptsRecipe(recipe: Recipe): Boolean {
    val prototype = prototype
    if (recipe.prototype.category !in prototype.crafting_categories) return false
    if (prototype is AssemblingMachinePrototype) {
        if (prototype.fixed_recipe.value.isNotEmpty() && prototype.fixed_recipe.value != recipe.prototype.name) return false
    }
    // simplified, not 100% accurate, but good enough for now
    if (recipe.inputs.keys.any { it is Fluid } || recipe.outputs.keys.any { it is Fluid }) {
        if (prototype.fluid_boxes.isNullOrEmpty()) return false
    }
    if (!recipe.acceptsModules(this.modulesUsed)) return false
    return true
}
