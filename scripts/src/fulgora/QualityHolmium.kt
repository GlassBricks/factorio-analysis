package scripts.fulgora

import glassbricks.factorio.recipes.SpaceAge
import glassbricks.factorio.recipes.problem.problem
import glassbricks.recipeanalysis.lp.LpOptions
import glassbricks.recipeanalysis.perSecond
import scripts.epic
import scripts.holmiumPlate
import scripts.printAndExportSolution

fun main(): Unit = with(SpaceAge) {
    val problem = fulgoraFactory1(scrapCost = 100).problem {
        fulgoraConfig1()
        output(holmiumPlate.withQuality(epic), rate = 1.0.perSecond)
    }

    val result = problem.solve(
        options = LpOptions(
            enableLogging = true
        )
    )
    printAndExportSolution("output/quality-holmium", result)
}
