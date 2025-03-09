package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.Energy
import glassbricks.recipeanalysis.Ingredient

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

fun parseEnergy(energy: Energy): Double {
    val (valueStr, unitStr) = Regex("""(\d+(?:\.\d+)?)([a-zA-Z]+)""").find(energy)!!.destructured
    val unitPrefix = unitStr.removeSuffix("J").removeSuffix("W")
    val value = valueStr.toDouble() * multipliers[unitPrefix]!!
    return if (unitStr.endsWith("W")) {
        value / 60
    } else if (unitStr.endsWith("J")) {
        value
    } else {
        error("Invalid energy: $energy")
    }
}

data object ElectricPower : Ingredient
