package scripts.fulgora

import glassbricks.factorio.recipes.SpaceAge
import glassbricks.factorio.recipes.problem.problem
import glassbricks.recipeanalysis.lp.LpOptions
import glassbricks.recipeanalysis.perMinute
import scripts.legendary
import scripts.printAndExportSolution
import scripts.qualityModule3
import scripts.undergroundBelt

fun main(): Unit = with(SpaceAge) {
    val factory = fulgoraFactory1(scrapCost = 150) {
        recipes {
            remove(undergroundBelt) // wants like 0.05 of it, not worth the complexity
        }
    }
    val problem = factory.problem {
        fulgoraConfig1()
        output(qualityModule3.withQuality(legendary), rate = 0.5.perMinute)
        surplusCost = 1e-5
    }
    val result = problem.solve(
        options = LpOptions(enableLogging = true)
    )
    printAndExportSolution("output/quality-modules", result)
}
