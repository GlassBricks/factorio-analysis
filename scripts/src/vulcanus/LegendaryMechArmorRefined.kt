package scripts.vulcanus

import glassbricks.factorio.prototypes.RecipeID
import glassbricks.factorio.recipes.ResearchConfig
import glassbricks.factorio.recipes.SpaceAge
import glassbricks.factorio.recipes.problem.factory
import glassbricks.factorio.recipes.problem.plus
import glassbricks.factorio.recipes.problem.problem
import glassbricks.factorio.recipes.times
import glassbricks.factorio.recipes.withModules
import glassbricks.recipeanalysis.div
import glassbricks.recipeanalysis.lp.LpOptions
import scripts.*
import kotlin.time.Duration.Companion.minutes

fun main(): Unit = with(SpaceAge) {
    val qqModule = qualityModule3.withQuality(quality = legendary)

    val theGoodRecycler = recycler.withModules(fill = qqModule)
    val theGoodEmp = electromagneticPlant.withModules(fill = qqModule)

    val theProdEmp = electromagneticPlant.withModules(
        fill = productivityModule2.withQuality(epic),
        beacons = listOf(beacon.withQuality(rare).withModules(speedModule3) * 9)
    )
    val highThroughputRecycling = listOf(
        substationRecycling, bigElectricPoleRecycling, cargoWagonRecycling, heatExchangerRecycling
    )

    val research = ResearchConfig(
        miningProductivity = 0.2,
        recipeProductivity = mapOf(
            RecipeID(castingLowDensityStructure.prototype.name) to 0.1,
        ),
    )
    val basicFactory = factory {
        this.research = research

//        val installableModules = listOf(
//            qualityModule,
//            productivityModule,
//            speedModule,
//            speedModule3
//        ) + listOf(
//            speedModule2,
//            qualityModule2,
//            productivityModule2,
//        ).flatMap { qualities.map { q -> it.withQuality(q) } }
//
//        vulcanusMachines(installableModules)
        vulcanusMachines()

        recipes {
            default {
                allQualities()
                cost = 1.0
            }
            allCraftingRecipes()
            calciteMining()
            coalMining { cost = 60.0 }
            tungstenOreMining()
            remove(electromagneticPlant)
            remove(lightningCollector)
            remove(lightningRod)
            remove(supercapacitorRecycling)
            remove(superconductorRecycling)
        }
    }
    val specialized = factory {
//        includeBuildCosts()
        this.research = research
        machines {
            recycler {
                qualities = setOf(rare)
                moduleConfig(fill = qqModule)
            }
        }
        recipes {
            default { allQualities() }
            highThroughputRecycling.forEach { addConfig(it) }
        }
    }

    val targetTime = 40.minutes

    val factory = basicFactory + specialized
    val problem = factory.problem {
        input(lava, cost = 0.0)
        input(sulfuricAcid, cost = 0.0005)
        input(electronicCircuit, limit = 5e6 / targetTime, cost = 0.0)
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

            limit(qualityModule3, 0)
            limit(qualityModule3.withQuality(uncommon), 0.0)
            limit(qualityModule3.withQuality(rare), 0.0)

            forbidAllUnspecified()
        }
    }

    val result = problem.solve(
        options = LpOptions(
            enableLogging = true
        )
    )
    println("Status: ${result.status}")
    result.solution?.let {
        printAndExportSolution("output/legendary-mech-armor2", it)
    }

}
