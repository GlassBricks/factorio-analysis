package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.*
import glassbricks.recipeanalysis.Ingredient

sealed interface Power : Ingredient {
    data object Electric : Power
    data object Heat : Power
    data class Burner(val fuelCategoryValue: String) : Power {
        val fuelCategory: FuelCategoryID get() = FuelCategoryID(fuelCategoryValue)
        override fun toString(): String = "Burner($fuelCategoryValue)"
    }

    companion object {
        fun Burner(fuelCategory: FuelCategoryID) = Burner(fuelCategory.value)
    }

    data object Unknown : Power
}

interface WithPowerUsage {
    val powerType: Power?
    val powerUsage: Double
}

fun EnergySource.toPowerType(): Power? = when (this) {
    is ElectricEnergySource -> Power.Electric
    is HeatEnergySource -> Power.Heat
    is BurnerEnergySource -> Power.Burner(this.fuel_categories?.let {
        it.singleOrNull() ?: TODO("Multiple fuel categories for one thing")
    } ?: FuelCategoryID("<any>"))

    is VoidEnergySource, is FluidEnergySource -> Power.Unknown
}

/**
 * Parses energy and considers effectivity
 */
fun getPowerUsage(energy: Energy, energySource: EnergySource): Double = when (energySource) {
    is ElectricEnergySource, is HeatEnergySource -> parseEnergy(energy)
    is BurnerEnergySource -> parseEnergy(energy) / energySource.effectivity
    is FluidEnergySource -> parseEnergy(energy) / energySource.effectivity
    is VoidEnergySource -> 0.0
}

private val multipliers = mapOf(
    "k" to 1e3,
    "M" to 1e6,
    "G" to 1e9,
    "T" to 1e12,
    "P" to 1e15,
    "E" to 1e18,
    "Z" to 1e21,
    "Y" to 1e24,
    "R" to 1e27,
    "Q" to 1e30,
    "" to 1.0,
)

/**
 * Note: both joules and watts are treated as the same unit, since the default time unit is _seconds_.
 *
 * The wiki says that watts are converted to joules/tick, by dividing by 60; but we don't do that here
 * since we use seconds instead of ticks.
 */
fun parseEnergy(energy: Energy): Double {
    val (valueStr, unitStr) = Regex("""(\d+(?:\.\d+)?)([a-zA-Z]+)""").find(energy)!!.destructured
    val unitPrefix = unitStr.removeSuffix("J").removeSuffix("W")
    val value = valueStr.toDouble() * multipliers[unitPrefix]!!
    return when {
        unitStr.endsWith("W") || unitStr.endsWith("J") -> value
        else -> error("Invalid energy: $energy")
    }
}

fun FactorioPrototypes.getFuelUsageRecipes(): List<FuelBurning> {
    return prototypes.items.values
        .filter { it.prototype.fuel_category.value.isNotBlank() }
        .map { FuelBurning(it) }
}
