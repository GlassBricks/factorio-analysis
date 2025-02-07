package glassbricks.recipeanalysis.recipelp

import glassbricks.recipeanalysis.*
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
    val lpProcesses: Vector<PseudoProcess>
    val processes: Vector<Process>
    val inputs: Vector<Ingredient>
    val outputs: Vector<Ingredient>
    val otherProcesses: Vector<PseudoProcess>
    val surpluses: Vector<Ingredient>
    val symbolUsage: Vector<Symbol>
    val throughputs: Map<Ingredient, Throughput>
    val objectiveValue: Double
}

data class RecipeLpSolution(
    override val lpProcesses: Vector<PseudoProcess>,
    override val surpluses: Vector<Ingredient>,
    override val symbolUsage: Vector<Symbol>,
    override val objectiveValue: Double,
) : Usages {
    override val processes: Vector<Process> = lpProcesses.vectorMapKeysNotNull { (it as? LpProcess)?.process }
    override val inputs: Vector<Ingredient> = lpProcesses.vectorMapKeysNotNull { (it as? Input)?.ingredient }
    override val outputs: Vector<Ingredient> = lpProcesses.vectorMapKeysNotNull { (it as? Output)?.ingredient }
    override val otherProcesses: Vector<PseudoProcess> =
        lpProcesses.vectorFilterKeys { !(it is Input || it is Output || it is LpProcess) }

    override val throughputs: Map<Ingredient, Throughput> by lazy {
        val consumption = mutableMapOf<Ingredient, Double>()
        val production = mutableMapOf<Ingredient, Double>()
        for ((process, usage) in lpProcesses) {
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
