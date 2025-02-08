import glassbricks.factorio.prototypes.RecipeID
import glassbricks.factorio.recipes.*
import glassbricks.factorio.recipes.problem.factory
import glassbricks.factorio.recipes.problem.problem
import glassbricks.recipeanalysis.lp.LpOptions
import glassbricks.recipeanalysis.perMinute
import glassbricks.recipeanalysis.recipelp.textDisplay
import glassbricks.recipeanalysis.writeTo
import java.io.File

fun main() {
    val vulcanusFactory = SpaceAge.factory {
        val modules = listOf(
            speedModule,
            speedModule2,
            productivityModule,
            productivityModule2,
        )
        machines {
            default {
                includeBuildCosts()
                moduleConfig()

                for (q in prototypes.qualities) {
                    for (module in modules) {
                        moduleConfig(fill = module.withQuality(q))
                        moduleConfig(
                            fill = module.withQuality(q),
                            beacons = listOf(beacon(fill = speedModule2, sharing = 6.0))
                        )
                    }
                    moduleConfig(fill = qualityModule.withQuality(q))
                    moduleConfig(fill = qualityModule2.withQuality(q))
                }
            }
            bigMiningDrill {}
            electromagneticPlant {
//                integralCost()
//                semiContinuousCost(1.0)
            }
            assemblingMachine3 {
//                integralCost()
            }
            foundry {
//                integral()
            }
            recycler() // not integral as we can easily share recyclers
            chemicalPlant()
            oilRefinery()
        }
        researchConfig = ResearchConfig(
            miningProductivity = 0.2,
            recipeProductivity = mapOf(
                RecipeID(castingLowDensityStructure.prototype.name) to 0.2,
            ),
            maxQuality = epic
        )
        recipes {
            default {
                allQualities()
            }
            allCraftingRecipes()
            calciteMining()
            coalMining {
                cost = 30.0 // coal is scarce
            }
            remove(substation)
        }
    }

    val production = vulcanusFactory.problem {
        input(lava, cost = 0.0)
        input(sulfuricAcid, cost = 0.005)
        output(
            qualityModule2.withQuality(epic),
            rate = 5.perMinute
        )
//        output(
//            electronicCircuit.withQuality(uncommon),
//            rate = 8.perMinute
//        )

        costs {
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
            for (module in listOf(speedModule, productivityModule, qualityModule)) {
                addQualityCost(module, 1.0)
            }
            for (module in listOf(speedModule2, productivityModule2, qualityModule2)) {
                addQualityCost(module, 5.0)
            }
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

    val dotFile = File("output/module2.dot")
    solution.toFancyDotGraph {
        clusterItemsByQuality()
        clusterRecipesByQuality()
//        dotGraph.enforceEdgeTopBottom()
        flipEdgesForMachine(SpaceAge.recycler)
    }.writeTo(dotFile)

    File("output/module2.txt").writeText(display)

    solution.toBlueprint().exportTo(File("output/module2-bp.txt"))
}

// command to generate pdf from dot file
// dot -Tpdf module2.dot -o module2.pdf
