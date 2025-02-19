import glassbricks.factorio.recipes.Item
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
import java.io.File

fun main() {
    val nauvisFactory = SpaceAge.factory {
        machines {
            electromagneticPlant()
            assemblingMachine3()
            recycler()
            default {
                includeBuildCosts()
                moduleConfig() // no modules
                for (module in module123AllQualities) {
                    moduleConfig(fill = module)
                    if (module.effects.quality <= 0) {
                        for (beaconConfig in speed2Beacons) {
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
            fun addQualityCost(item: Item, baseCost: Double) {
                costOf(item, baseCost)
                costOf(item.withQuality(uncommon), baseCost * 5)
                costOf(item.withQuality(rare), baseCost * 5 * 4)
                costOf(item.withQuality(epic), baseCost * 5 * 4 * 3)
                costOf(item.withQuality(legendary), baseCost * 5 * 4 * 3 * 3)
            }
            for (module in module1s) {
                addQualityCost(module, 1.0)
            }
            for (module in module2s) {
                addQualityCost(module, 5.0)
            }
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

// command to generate pdf from dot file
// dot -Tpdf module2.dot -o module2.pdf
