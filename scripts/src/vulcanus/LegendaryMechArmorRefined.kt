package scripts.vulcanus

import glassbricks.factorio.prototypes.RecipeID
import glassbricks.factorio.recipes.*
import glassbricks.factorio.recipes.export.RecipesFirst
import glassbricks.factorio.recipes.problem.factory
import glassbricks.factorio.recipes.problem.plus
import glassbricks.factorio.recipes.problem.problem
import glassbricks.recipeanalysis.Ingredient
import glassbricks.recipeanalysis.div
import glassbricks.recipeanalysis.lp.LpOptions
import scripts.*
import kotlin.time.Duration.Companion.minutes

fun main(): Unit = with(SpaceAge) {
    val theGoodRecycler =
        recycler
            .withQuality(rare)
            .withModules(fill = qualityModule3.withQuality(quality = legendary))
    val theGoodEmp = electromagneticPlant
        .withQuality(rare)
        .withModules(fill = qualityModule2.withQuality(quality = legendary))
    val theProdEmp = electromagneticPlant
        .withQuality(rare)
        .withModules(
            fill = productivityModule2.withQuality(epic),
            beacons = listOf(beacon.withQuality(rare).withModules(speedModule3) * 4)
        )

    val research = ResearchConfig(
        miningProductivity = 0.2,
        recipeProductivity = mapOf(
            RecipeID(castingLowDensityStructure.prototype.name) to 0.1,
        ),
    )
    val basicStuff = factory {
        this.research = research

        // no quality modules to start with to start with
        val installableModules = listOf(
            qualityModule,
            qualityModule2,
            // maybe we can beg fulgora for just a few quality 3s?
            productivityModule,
            productivityModule2,
            speedModule,
            speedModule2,
            speedModule3
        )

        vulcanusMachines(
            installableModules, beacons =
                beaconsWithSharing(speedModule3).map { listOf(it) } +
                        listOf(listOf(beacon.withModules(fill = speedModule2)))
        )
//        vulcanusMachines()

        recipes {
            default {
                allQualities()
                cost = 2.0
            }
            allCraftingRecipes()
            calciteMining()
            coalMining { cost = 60.0 }
            tungstenOreMining()

            advancedCircuit {
                // only does a tiny bit -- let's just not do it
                qualities -= normal
            }

            // can't craft
            remove(electromagneticPlant)
            remove(lightningCollector)
            remove(lightningRod)
            // don't want to craft
            remove(supercapacitorRecycling)
            remove(superconductorRecycling)

            // 0.05 express belt foundries is too complicated
            remove(transportBelt)

            // really?
            remove(grenade)

            // night vision equipment is superior
            remove(energyShieldEquipment)
        }
    }
    val theGoodStuff = factory {
        includeMachineCount()
        this.research = research
        machines {
            recycler {
                onlyMatching(theGoodRecycler)
            }
            electromagneticPlant {
                qualities = setOf(rare)
                noEmptyModules()
                moduleConfig(theGoodEmp.moduleSet!!.toModuleSetConfig())
                moduleConfig(theProdEmp.moduleSet!!.toModuleSetConfig())
            }
            assemblingMachine3 {
                moduleConfig(fill = qualityModule3.withQuality(quality = legendary))
                cost = 1000.0
//                onlyRecipes(
//                    powerArmorMk2.recipe(),
//                    mechArmor.recipe(),
//                )
            }
        }
        recipes {
            default { allQualities() }
            allCraftingRecipes()


            remove(electromagneticPlant)
            remove(lightningCollector)
            remove(lightningRod)
            remove(supercapacitorRecycling)
            remove(superconductorRecycling)
        }
    }

    val targetTime = 30.minutes

    val factory = basicStuff + theGoodStuff
    val problem = factory.problem {
        input(lava, cost = 0.0)
        input(sulfuricAcid, cost = 0.0005)
        limit(holmiumPlate.withQuality(epic), 800.0 / targetTime)
        limit(supercapacitor.withQuality(epic), 200.0 / targetTime)
        limit(superconductor.withQuality(epic), 200.0 / targetTime)
        limit(holmiumPlate.withQuality(legendary), 50.0 / targetTime)
        limit(supercapacitor.withQuality(legendary), 12.5 / targetTime)
        limit(superconductor.withQuality(legendary), 12.5 / targetTime)

        oneFullLegendaryMechArmor(targetTime)

        costs {
            vulcanusMachineCosts1()
            vulcanusModuleCosts1()

            // approximate only getting to use it later (buffer stuff then craft)
            // Can be improved with more accurate multi-stage optimization shenanigans perhaps
            limit(MachineSymbol(theGoodRecycler), 0.6)
            limit(MachineSymbol(theGoodEmp), 0.6)
            limit(MachineSymbol(theProdEmp), 0.6)

            forbidAllUnspecified()
        }
    }

    val result = problem.solve(
        options = LpOptions(
            enableLogging = true
        )
    )
    println("Status: ${result.status}")
    printAndExportSolution("output/legendary-mech-armor2", result, object : RecipesFirst {
        override fun formatInputRate(input: Ingredient, rate: Double): String {
            val amount = rate * targetTime.inWholeSeconds
            val amountFnt = "%.2f".format(amount)
            return super.formatInputRate(input, rate) + " (${amountFnt})"
        }
    })

}
