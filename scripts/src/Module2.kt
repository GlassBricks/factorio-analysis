import glassbricks.factorio.prototypes.RecipeID
import glassbricks.factorio.recipes.Item
import glassbricks.factorio.recipes.ResearchConfig
import glassbricks.factorio.recipes.SpaceAge
import glassbricks.factorio.recipes.export.*
import glassbricks.factorio.recipes.problem.factory
import glassbricks.factorio.recipes.problem.problem
import glassbricks.recipeanalysis.lp.LpOptions
import glassbricks.recipeanalysis.perMinute
import glassbricks.recipeanalysis.recipelp.textDisplay
import glassbricks.recipeanalysis.recipelp.toThroughputGraph
import glassbricks.recipeanalysis.writeDotGraph
import java.io.File

fun main() {
    val vulcanusFactory = SpaceAge.factory {
        vulcanusMachines()
        researchConfig = ResearchConfig(
            miningProductivity = 0.2,
            recipeProductivity = mapOf(
                RecipeID(castingLowDensityStructure.prototype.name) to 0.2,
            ),
            maxQuality = epic
        )
        recipes {
            default { allQualities() }
            allCraftingRecipes()
            calciteMining()
            coalMining { cost = 35.0 /* coal is scarce */ }
            tungstenOreMining { cost = 10.0 }
        }
    }

    val production = vulcanusFactory.problem {
        input(lava, cost = 0.0)
        input(sulfuricAcid, cost = 0.005)
        output(
            qualityModule2.withQuality(epic),
            rate = 5.perMinute
        )

        costs {
            costOf(assemblingMachine2.item(), 1 + 1.0)
            costOf(assemblingMachine3.item(), 3 + 1.0)
            costOf(foundry.item(), 5 + 2.0)
            costOf(recycler.item(), 10 + 1.0)
            costOf(electromagneticPlant.item(), 100)
            costOf(beacon.item(), 4 + 1.0)
            costOf(bigMiningDrill.item(), 5 + 2)

            //            val qualityCostMultiplier = 5.0
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
            numThreads = Runtime.getRuntime().availableProcessors() - 2,
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
    File("output/module2.txt").writeText(display)
    File("output/module2.dot").writeDotGraph(graph)
    solution.toBlueprint().exportTo(File("output/module2-bp.txt"))
}

// command to generate pdf from dot file
// dot -Tpdf module2.dot -o module2.pdf
