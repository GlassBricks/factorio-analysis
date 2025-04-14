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

        limit(fruit, rate = 2.0.perSecond)
        input(water, cost = 0.0)
        maximize(agriculturalSciencePack)
        surplusCost = 0.0

        customProcess("jellynut") {
            ingredientRate = vectorOfWithUnits(
                jellynut to 1.0
            )
        }

        customProcess("yumako") {
            ingredientRate = vectorOfWithUnits(
                yumako to 1.0
            )
        }
    }

    val result = problem.solve()
    printAndExportSolution("output/agri-sci", result)
}
