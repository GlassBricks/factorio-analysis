package scripts.vulcanus

import glassbricks.factorio.prototypes.RecipeID
import glassbricks.factorio.recipes.*
import glassbricks.factorio.recipes.export.*
import glassbricks.factorio.recipes.problem.factory
import glassbricks.factorio.recipes.problem.plus
import glassbricks.factorio.recipes.problem.problem
import glassbricks.recipeanalysis.lp.LpOptions
import glassbricks.recipeanalysis.perMinute
import glassbricks.recipeanalysis.recipelp.textDisplay
import glassbricks.recipeanalysis.recipelp.toThroughputGraph
import glassbricks.recipeanalysis.writeDotGraph
import scripts.*
import java.io.File

fun main() = with(SpaceAge) {

    val installableModules = listOf(
        qualityModule,
        qualityModule2,
        // maybe we can beg fulgora for just a few quality 3s?
        productivityModule,
        productivityModule2,
        speedModule,
        speedModule2,
        speedModule3
    )

    val basicFactory = factory {
        vulcanusMachines(installableModules)
        research = ResearchConfig(
            miningProductivity = 0.2,
            recipeProductivity = mapOf(
                RecipeID(castingLowDensityStructure.prototype.name) to 0.1,
            ),
        )
        recipes {
            default { allQualities() }
            allCraftingRecipes()
            calciteMining()
            coalMining { cost = 40.0 /* coal patches are scarce */ }
            tungstenOreMining { cost = 10.0 }
            remove(transportBelt)
            remove(steamEngine)
            remove(qualityModule2Recycling)
            remove(qualityModuleRecycling)
            remove(grenade)
        }
    }
    val theGoodEmp = electromagneticPlant
//        .withQuality(rare)
        .withModules(fill = qualityModule2.withQuality(epic))
    val theProdEmp = electromagneticPlant
//        .withQuality(rare)
        .withModules(
            fill = productivityModule2.withQuality(epic), beacons = listOf(beacon.withModules(speedModule3) * 12)
        )
    val theGoodRecycler =
        recycler
//            .withQuality(rare)
            .withModules(fill = qualityModule2.withQuality(quality = legendary))
    val specialFactory = factory {
        includeMachineCount()
        machines {
            electromagneticPlant {
                noEmptyModules()
                moduleConfig(theGoodEmp.moduleSet.toModuleSetConfig())
                moduleConfig(theProdEmp.moduleSet.toModuleSetConfig())
            }
            recycler {
                onlyMatching(theGoodRecycler)
            }
        }
        recipes {
            default { allQualities() }
            allCraftingRecipes()
            remove(qualityModule2Recycling)
            remove(qualityModuleRecycling)
        }
    }
    val factory = basicFactory + specialFactory

    val production = factory.problem {
        input(lava, cost = 0.0)
        input(sulfuricAcid, cost = 0.001)
        output(
            qualityModule2.withQuality(epic),
            rate = 4.perMinute
        )
        output(
            qualityModule2.withQuality(legendary),
            rate = 1.perMinute
        )
        output(steelPlate.withQuality(epic), rate = 20.perMinute)

        costs {
            vulcanusMachineCosts1()
            vulcanusModuleCosts1()
            forbidUnspecifiedModules()
            limitMachine(theGoodEmp, 1.0)
            limitMachine(theProdEmp, 1.0)
            limitMachine(theGoodRecycler, 1.0)
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
