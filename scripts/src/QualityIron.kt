import glassbricks.factorio.recipes.FactorioShorthandFormatter
import glassbricks.factorio.recipes.Item
import glassbricks.factorio.recipes.SpaceAge
import glassbricks.factorio.recipes.invoke
import glassbricks.factorio.recipes.problem.factory
import glassbricks.factorio.recipes.problem.problem
import glassbricks.recipeanalysis.lp.LpOptions
import glassbricks.recipeanalysis.perSecond
import glassbricks.recipeanalysis.recipelp.textDisplay
import kotlin.math.pow

fun main() {
    val vulcanusFactory = SpaceAge.factory {
        machines {

            val modules = listOf(
                speedModule,
                speedModule2,
                productivityModule,
                productivityModule2,
            )
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
            chemicalPlant()
            oilRefinery()
            assemblingMachine3()

            foundry()
            recycler()
            bigMiningDrill()
        }
        recipes {
            default {
                allQualities()
            }
            allCraftingRecipes()
            calciteMining()
            coalMining()
        }
    }

    val production = vulcanusFactory.problem {
        input(lava, cost = 0.0)
//        input(sulfuricAcid, cost = 0.0005)
        input(lubricant, cost = 0.0005)

        output(ironPlate.withQuality(legendary), 1.0.perSecond)

        costs {
            costOf(assemblingMachine3.item(), 3 + 1.0)
            costOf(foundry.item(), 5 + 2.0)
            costOf(recycler.item(), 10 + 1.0)
            costOf(electromagneticPlant.item(), 100)
            costOf(beacon.item(), 4 + 1.0)
            costOf(bigMiningDrill.item(), 5)

            val qualityCostMultiplier = 5.0
            fun addQualityCost(item: Item, baseCost: Double) {
                for ((index, quality) in prototypes.qualities.withIndex()) {
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

    }
    val solution = production.solve(
        options = LpOptions(epsilon = 1e-5)
    )
    println(solution.status)
    println(solution.lpResult.bestBound)
    println(solution.solution?.textDisplay(FactorioShorthandFormatter))
}
