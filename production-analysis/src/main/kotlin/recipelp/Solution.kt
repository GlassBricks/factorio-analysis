package glassbricks.recipeanalysis.recipelp

import glassbricks.recipeanalysis.Ingredient
import glassbricks.recipeanalysis.Symbol
import glassbricks.recipeanalysis.Vector
import glassbricks.recipeanalysis.lp.LpResult
import glassbricks.recipeanalysis.lp.LpResultStatus
import glassbricks.recipeanalysis.lp.LpSolution

data class Throughput(
    val production: Double,
    val consumption: Double,
) {
    val net: Double get() = production - consumption
    val min: Double get() = maxOf(production, consumption)
}

interface Usages {
    val symbolUsage: Vector<Symbol>
    val surpluses: Vector<Ingredient>
    val recipeUsage: Vector<PseudoProcess>
    val throughputs: Map<Ingredient, Throughput>
    val objectiveValue: Double
}

data class RecipeLpSolution(
    override val recipeUsage: Vector<PseudoProcess>,
    override val surpluses: Vector<Ingredient>,
    override val symbolUsage: Vector<Symbol>,
    override val objectiveValue: Double,
) : Usages {
    override val throughputs by lazy {
        val consumption = mutableMapOf<Ingredient, Double>()
        val production = mutableMapOf<Ingredient, Double>()
        for ((process, usage) in recipeUsage) {
            for ((ingredient, baseRate) in process.ingredientRate) {
                val rate = baseRate * usage
                val thisConsumption = consumption.getOrPut(ingredient) { 0.0 }
                val thisProduction = production.getOrPut(ingredient) { 0.0 }
                if (rate > 0) {
                    production[ingredient] = thisProduction + rate
                } else {
                    consumption[ingredient] = thisConsumption - rate
                }
            }
        }
        production.mapValues { (ingredient, rate) ->
            Throughput(rate, consumption[ingredient] ?: 0.0)
        }
    }
}

data class RecipeLpResult(
    val lpResult: LpResult,
    val solution: RecipeLpSolution?,
) {
    val lpSolution: LpSolution? get() = lpResult.solution
    val status: LpResultStatus get() = lpResult.status
}
