import glassbricks.factorio.recipes.*
import glassbricks.factorio.recipes.problem.factory
import glassbricks.factorio.recipes.problem.problem
import glassbricks.recipeanalysis.LpOptions
import glassbricks.recipeanalysis.OrToolsLp
import glassbricks.recipeanalysis.perMinute
import java.io.File
import kotlin.math.pow
import kotlin.time.Duration.Companion.minutes

fun main() {
    val vulcanusFactory = SpaceAge.factory {
        /*  val beacons = listOf(
              speedModule,
              speedModule2
          ).flatMap { module ->
              qualities.flatMap { q ->
                  listOf(
                      beacon(fill = module.withQuality(q), sharing = 8.0),
                      beacon(fill = module.withQuality(q), sharing = 6.0) * 2,
                  )
              }
          }*/

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
            miningProductivity = 0.2
        )
        recipes {
            default {
                allQualities()
            }
            allRecipes()
            calciteMining()
            coalMining()
        }
    }

    val production = vulcanusFactory.problem {
        input(lava, cost = 0.0)
        input(sulfuricAcid, cost = 0.0005)

        costs {
            costOf(assemblingMachine3.item(), 3 + 1.0)
            costOf(foundry.item(), 5 + 2.0)
            costOf(recycler.item(), 10 + 1.0)
            costOf(electromagneticPlant.item(), 100)
            costOf(beacon.item(), 4 + 1.0)
            costOf(bigMiningDrill.item(), 5 + 2)

            val qualityCostMultiplier = 5.0
            fun addQualityCost(item: Item, baseCost: Double) {
                for ((index, quality) in qualities.withIndex()) {
                    var value = baseCost * qualityCostMultiplier.pow(index)
                    costOf(item.withQuality(quality), value)
                }
            }
            for (module in listOf(speedModule, productivityModule, qualityModule)) {
                addQualityCost(module, 1.0)
            }
            for (module in listOf(speedModule2, productivityModule2, qualityModule2)) {
                addQualityCost(module, 5.0)
            }
        }

        output(
            qualityModule2.withQuality(epic),
            rate = 4.perMinute
        )
        lpSolver = OrToolsLp("SCIP")
        lpOptions = LpOptions(
            timeLimit = 10.minutes,
            numThreads = Runtime.getRuntime().availableProcessors() - 2,
            epsilon = 1e-5
        )
    }
    val result = production.solve()
    println("Status: ${result.status}")
    println("Best bound: ${result.lpResult.bestBound}")
    result.solution?.let {
        println("Objective: ${result.lpSolution!!.objectiveValue}")
        val display = it.display(compareBy {
            (it as? MachineSetup<*>)?.process?.toString()
        })
        println(display)
        File("output/module2.txt").writeText(display)

        it.toBlueprint().exportTo(File("output/module2-bp.txt"))
    }
}
