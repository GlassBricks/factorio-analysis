package scripts

import glassbricks.factorio.recipes.SpaceAge
import glassbricks.factorio.recipes.problem.factory
import glassbricks.factorio.recipes.problem.problem
import glassbricks.recipeanalysis.perSecond

fun main(): Unit = with(SpaceAge) {
    val everything = factory {
        machines {
            default {
                cost = 1.0
            }
            allCraftingMachines()
        }
        recipes {
            allCraftingRecipes()
        }
    }
    val production = everything.problem {
        surplusCost = 1000.0
        input(copperPlate, limit = 15.perSecond, cost = -1e5)
        input(ironPlate, limit = 15.perSecond, cost = -1e5)
        input(steelPlate, limit = 15.perSecond, cost = -1e5)
    }

    val result = production.solve()
    printAndExportSolution("output/void-iron", result)
}
