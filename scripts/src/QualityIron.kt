import glassbricks.factorio.recipes.*
import glassbricks.recipeanalysis.LpOptions
import glassbricks.recipeanalysis.OrToolsLp

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
        }
        recipes {
            default {
                allQualities()
            }
            allRecipes()
        }
    }

    val production = vulcanusFactory.problem {
        input(moltenIron, cost = 0.0)
        input(moltenCopper, cost = 0.0)

        maximize(ironPlate.withQuality(epic))

        costs {
            costOf(speedModule, 1)
            costOf(assemblingMachine3.item(), 3)
            costOf(speedModule2, 5)
            costOf(productivityModule2, 5)
            costOf(foundry.item(), 5)

            limit(qualityModule2, 100)
            limit(qualityModule2.withQuality(uncommon), 20)
            limit(qualityModule2.withQuality(rare), 4)
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
