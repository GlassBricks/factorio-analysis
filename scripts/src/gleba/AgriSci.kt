package scripts.gleba

import glassbricks.factorio.recipes.SpaceAge
import glassbricks.factorio.recipes.problem.factory
import glassbricks.factorio.recipes.problem.problem
import glassbricks.factorio.recipes.withModules
import glassbricks.recipeanalysis.Ingredient
import glassbricks.recipeanalysis.perSecond
import glassbricks.recipeanalysis.vectorOfWithUnits
import scripts.*

fun main(): Unit = with(SpaceAge) {
    val factory = factory {
        includeBuildCosts()
        includePowerUsage()
        glebaMachines(
            modules = listOf(
                productivityModule,
                productivityModule2,
//                speedModule,
//                speedModule2,
                efficiencyModule,
            ),
            beacons = listOf(
                listOf(beacon.withModules(fill = speedModule)),
                listOf(beacon.withModules(fill = speedModule2))
            )
        )
        recipes {
            default {
                cost = 1.0
            }
            allCraftingRecipes()
        }
    }
    val problem = factory.problem {
        val fruit = Ingredient("AnyFruit")
        val shippedScience = Ingredient("ShippedScience")

        limit(fruit, rate = 2.0.perSecond)
        input(water, cost = 0.0)
        maximize(shippedScience)
        surplusCost = 0.0

        customProcess("jellynut") {
            ingredientRate = vectorOfWithUnits(
                fruit to -1.0,
                jellynut to 1.0
            )
        }

        customProcess("yumako") {
            ingredientRate = vectorOfWithUnits(
                fruit to -1.0,
                yumako to 1.0
            )
        }

        customProcess("ScienceShipment") {
            ingredientRate = vectorOfWithUnits(
                agriculturalSciencePack to -1000.0,
                rocketFuel to -50.0 / (1.24),
                shippedScience to 1000.0
            )
            ingredientRate /= 100
        }
    }

    val result = problem.solve()
    printAndExportSolution("output/agri-sci", result)
}
