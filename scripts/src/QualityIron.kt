import glassbricks.factorio.recipes.*
import glassbricks.factorio.recipes.problem.factory
import glassbricks.factorio.recipes.problem.problem
import glassbricks.recipeanalysis.LpOptions
import glassbricks.recipeanalysis.OrToolsLp
import glassbricks.recipeanalysis.perSecond

fun main() {
    val vulcanusFactory = SpaceAge.factory {
        machines {
            default {
                includeBuildCosts()

                moduleConfig()
                val beacon6 = beacon(fill = speedModule2, sharing = 6.0)
                moduleConfig(fill = speedModule, beacons = listOf(beacon6))
                moduleConfig(fill = speedModule, beacons = listOf(beacon6 * 4))

                moduleConfig(fill = productivityModule2, beacons = listOf(beacon6))
                moduleConfig(fill = productivityModule2, beacons = listOf(beacon6 * 4))

                moduleConfig(fill = qualityModule2)
                moduleConfig(fill = qualityModule2.withQuality(uncommon))
                moduleConfig(fill = qualityModule2.withQuality(rare))
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
            allRecipes()
            calciteMining()
        }
    }

    val production = vulcanusFactory.problem {
        input(lava, cost = 0.0)

        output(ironPlate.withQuality(epic), 1.0.perSecond)

        costs {
            costOf(speedModule, 1)
            costOf(assemblingMachine3.item(), 3)
            costOf(speedModule2, 5)
            costOf(productivityModule2, 5)
            costOf(qualityModule2, 5)
            costOf(foundry.item(), 5)
            costOf(recycler.item(), 5)
        }

        lpOptions = LpOptions(
            solver = OrToolsLp("CBC"),
            epsilon = 1e-5
        )
    }
    val solution = production.solve()
    println(solution.status)
    println(solution.recipeSolution?.display())
}
