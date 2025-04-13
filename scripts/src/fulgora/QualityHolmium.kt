package scripts.fulgora

import glassbricks.factorio.recipes.SpaceAge
import glassbricks.factorio.recipes.problem.problem
import glassbricks.recipeanalysis.lp.LpOptions
import glassbricks.recipeanalysis.perSecond
import scripts.epic
import scripts.foundry
import scripts.holmiumPlate
import scripts.printAndExportSolution

fun main(): Unit = with(SpaceAge) {
    val fulgoraFactory1 = fulgoraFactory1(scrapCost = 400) {
        machines {
            foundry {
                moduleSetConfigs.removeIf {
                    it.modulesUsed.any { m -> "quality" in m.prototype.name }
                }
            }
        }
    }
    val problem = fulgoraFactory1.problem {
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
