package scripts.vulcanus

import glassbricks.factorio.prototypes.RecipeID
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
import scripts.*
import java.io.File

fun main() {
    val vulcanusFactory = SpaceAge.factory {
        vulcanusMachines()
        researchConfig = ResearchConfig(
            miningProductivity = 0.2,
            recipeProductivity = mapOf(
                RecipeID(castingLowDensityStructure.prototype.name) to 0.1,
            ),
            maxQuality = epic
        )
        recipes {
            default { allQualities() }
            allCraftingRecipes()
            calciteMining()
            coalMining { cost = 70.0 /* coal patches are scarce */ }
            tungstenOreMining { cost = 10.0 }
            remove(transportBelt)
            remove(qualityModule2Recycling)
            remove(qualityModuleRecycling)
        }
    }

    val production = vulcanusFactory.problem {
        input(lava, cost = 0.0)
        input(sulfuricAcid, cost = 0.001)
        output(
            qualityModule2.withQuality(rare),
            rate = 10.perMinute
        )
        output(
            qualityModule2.withQuality(epic),
            rate = 5.perMinute
        )
        output(steelPlate.withQuality(epic), rate = 60.perMinute)

        costs {
            vulcanusCosts1()
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
    File("output/module2.txt").writeText(display)
    File("output/module2.dot").writeDotGraph(graph)
    solution.toBlueprint().exportTo(File("output/module2-bp.txt"))
}

// command to generate pdf from dot file
// dot -Tpdf module2.dot -o module2.pdf
