package glassbricks.factorio.recipes

import glassbricks.recipeanalysis.Ingredient
import glassbricks.recipeanalysis.IngredientRate
import glassbricks.recipeanalysis.vectorOfWithUnits

/** Pseudo recipe, converting fuel into a fuel category's power */
data class FuelBurning(val fuel: Item) : FactorioPseudoProcess {
    init {
        require(fuel.prototype.fuel_category.value.isNotBlank()) {
            "Fuel must have a fuel category"
        }
    }

    override val netInputs: Set<Ingredient> get() = setOf(fuel)
    override val netOutputs: Set<Ingredient> get() = setOf(Power.Burner(fuel.prototype.fuel_category))
    override val netRate: IngredientRate
        get() = vectorOfWithUnits(
            fuel to -1.0,
            netOutputs.first() to parseEnergy(fuel.prototype.fuel_value)
        )

    override fun toString(): String = "FuelUsage($fuel)"
}
