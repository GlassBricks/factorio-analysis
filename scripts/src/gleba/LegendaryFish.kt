package scripts.gleba

import glassbricks.factorio.recipes.BurnerPower
import glassbricks.factorio.recipes.SpaceAge
import glassbricks.factorio.recipes.parseEnergy
import glassbricks.factorio.recipes.problem.factory
import glassbricks.factorio.recipes.problem.problem
import glassbricks.factorio.recipes.withModules
import glassbricks.recipeanalysis.perSecond
import glassbricks.recipeanalysis.vectorOfWithUnits
import scripts.*

fun main(): Unit = with(SpaceAge) {
    val factory = factory {
        includeBuildCosts()
        includePowerUsage()
        machines {
            default {
                moduleConfigWithBeacons(
                    modules = listOf(
                        productivityModule,
                        productivityModule2,
                        speedModule,
                        speedModule2,
                        efficiencyModule,
                        qualityModule2.withQuality(uncommon)
                    ),
                    beacons = listOf(
                        listOf(beacon.withModules(fill = speedModule)),
                        listOf(beacon.withModules(fill = speedModule2))
                    )
                )
            }
            assemblingMachine3()
            biochamber()
            recycler()
        }
        recipes {
            default {
                cost = 1.0
                allQualities()
            }
            allCraftingRecipes()
            fishBreeding {
                qualities -= legendary
            }
        }
    }

    val problem = factory.problem {
        limit(bioflux, rate = 2.0.perSecond / 60.0) // 2 biter spawners
        input(water, cost = 0.0)
        maximize(rawFish.withQuality(legendary))

        customProcess("Biter spawner") {
            ingredientRate = vectorOfWithUnits(
                BurnerPower("food") to -parseEnergy("100kW"),
                biterEgg to 0.5,
            )
        }
    }

    val result = problem.solve()
    result.solution?.let { solution ->
        val fishRate = solution.outputs[rawFish.withQuality(legendary)]
        println("Legendary fish rate: $fishRate")
        println("Legendary fish time: ${1 / fishRate / 60 / 60} hours")
    }
    printAndExportSolution("output/agri-sci", result)
}
