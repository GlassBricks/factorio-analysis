package scripts.nauvis

import glassbricks.factorio.recipes.SpaceAge
import glassbricks.factorio.recipes.export.RecipesFirst
import glassbricks.factorio.recipes.export.mergeItemsByQuality
import glassbricks.factorio.recipes.export.mergeRecipesByQuality
import glassbricks.factorio.recipes.export.toFancyDotGraph
import glassbricks.factorio.recipes.problem.factory
import glassbricks.factorio.recipes.problem.problem
import glassbricks.recipeanalysis.lp.LpOptions
import glassbricks.recipeanalysis.perMinute
import glassbricks.recipeanalysis.recipelp.textDisplay
import glassbricks.recipeanalysis.recipelp.toThroughputGraph
import glassbricks.recipeanalysis.writeDotGraph
import scripts.*
import scripts.vulcanus.vulcanusMachineCosts1
import scripts.vulcanus.vulcanusModuleCosts1
import java.io.File

fun main() = with(SpaceAge) {
    val nauvisFactory = factory {
        includeBuildCosts()
        machines {
            electromagneticPlant()
            assemblingMachine3()
            recycler()
            default {
                moduleConfig() // no modules
                for (module in nonEffModulesAllQualities) {
                    moduleConfig(fill = module)
                    if (module.effects.quality <= 0) {
                        for (beaconConfig in beaconsWithSharing(speedModule2)) {
                            moduleConfig(fill = module, beacons = listOf(beaconConfig))
                        }
                    }
                }
            }
        }
        recipes {
            default { allQualities() }
            allCraftingRecipes()
        }
    }

    val production = nauvisFactory.problem {
        limit(biterEgg, 200.perMinute)
        input(productivityModule2.withQuality(rare))
        input(productivityModule2.withQuality(epic))

        output(productivityModule3.withQuality(epic), rate = 1.perMinute)

        costs {
            vulcanusMachineCosts1()
            vulcanusModuleCosts1()
            forbidUnspecifiedModules()
        }

    }
    val result = production.solve(
        options = LpOptions(
            enableLogging = true,
            epsilon = 1e-5
        )
    )
    println("Status: ${result.status}")
    println("Best bound: ${result.lpResult.bestBound}")
    val solution = result.solution
    if (solution == null) return

    println("Objective: ${result.lpSolution!!.objectiveValue}")

    val display = solution.textDisplay(RecipesFirst)
    println(display)

    val graph = solution.toThroughputGraph {
        mergeItemsByQuality()
        mergeRecipesByQuality()
    }.toFancyDotGraph()
    File("output/module3.txt").writeText(display)
    File("output/module3.dot").writeDotGraph(graph)
}
