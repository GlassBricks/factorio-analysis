package scripts.vulcanus

import glassbricks.factorio.recipes.ElectricPower
import glassbricks.factorio.recipes.SpaceAge
import glassbricks.factorio.recipes.parseEnergy
import glassbricks.factorio.recipes.problem.factory
import glassbricks.factorio.recipes.problem.problem
import glassbricks.factorio.recipes.withModules
import glassbricks.recipeanalysis.Symbol
import glassbricks.recipeanalysis.div
import glassbricks.recipeanalysis.lp.LpOptions
import glassbricks.recipeanalysis.vectorOf
import glassbricks.recipeanalysis.vectorOfWithUnits
import scripts.*
import kotlin.time.Duration.Companion.hours

fun main(): Unit = with(SpaceAge) {
    val gcProducedSymbol = Symbol("gcProduced")
    val vulcanusFactory = factory {
        val modules = listOf(
            speedModule,
            speedModule2,
            speedModule3,
            efficiencyModule,
            efficiencyModule2,
            productivityModule,
            productivityModule2
        )
        val beaconModules = modules.filter { it.effects.productivity.toInt() == 0 }
        vulcanusMachines(
            modules = modules,
            beacons = beaconModules.flatMap {
                beaconsWithSharing(
                    it,
                    profiles = listOf(
                        BeaconProfile(8.0, 1),
                        BeaconProfile(8.0, 2),
                        BeaconProfile(6.0, 3),
                        BeaconProfile(6.0, 4),
                        BeaconProfile(4.0, 5),
                        BeaconProfile(4.0, 6),
                    )
                )
            }.flatMap {
                val beaconCount = it.beaconCount
                if (beaconCount.count > 1) {
                    listOf(
                        listOf(it),
                        listOf(
                            beaconCount.copy(count = beaconCount.count - 1),
                            beacon.withModules(efficiencyModule)
                        ),
                        listOf(
                            beaconCount.copy(count = beaconCount.count - 1),
                            beacon.withModules(efficiencyModule2)
                        )
                    )
                } else {
                    listOf(listOf(it))
                }
            }
        )
        machines {
            assemblingMachine2 { moduleSetConfigs.removeIf { it.beacons.isEmpty() } }
            assemblingMachine3 { moduleSetConfigs.removeIf { it.beacons.isEmpty() } }
        }
        recipes {
            allCraftingRecipes()
            calciteMining()
            coalMining { cost = 70.0 }
            tungstenOreMining { cost = 70.0 }

            // apparently infinity pipes have a recipe...
            remove(infinityPipe)
        }
        TODO()
//        setups.default {
//            val gcOutput = this.setup.netRate.sumOf { (ingredient, rate) ->
//                if (ingredient is Item && ingredient.prototype == electronicCircuit.prototype) rate else 0.0
//            }
//            if (gcOutput > 0.0) {
//                additionalCosts += vectorOf(gcProducedSymbol to gcOutput)
//            }
//        }
    }

//    val targetRate = 18e6 / 3.hours
    val targetRate = 18e6 / 2.hours

    val production = vulcanusFactory.problem {
        input(lava, cost = 0.0)
        input(sulfuricAcid, cost = 0.001)

        // hack to get power costs working
        customProcess("steam turbine power") {
            ingredientRate = vectorOfWithUnits(steam to -60.0)
            additionalCosts = vectorOf(ElectricPower to -parseEnergy("5.8MW"), steamTurbine to 1.0)
        }

        surplusCost = 0.1
        costs {
            // at least 20MGC produced somehow
            varConfig(gcProducedSymbol).apply {
                lowerBound = targetRate.ratePerSecond
            }
            // all power must be produced
            limit(ElectricPower, 0.0)
            vulcanusMachineCosts1()
            vulcanusModuleCosts1()
            forbidAllUnspecified()
        }
    }

    val result = production.solve(
        options = LpOptions(
            enableLogging = true,
            epsilon = 1e-5
        )
    )
    println("Status: ${result.status}")
    result.solution?.let {
        printAndExportSolution("output/20MGC", it)
    }
}
