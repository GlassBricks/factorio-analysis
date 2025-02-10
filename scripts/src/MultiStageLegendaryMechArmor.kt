import glassbricks.factorio.prototypes.RecipeID
import glassbricks.factorio.recipes.ResearchConfig
import glassbricks.factorio.recipes.SpaceAge
import glassbricks.factorio.recipes.export.FactorioGraphExportFormatter
import glassbricks.factorio.recipes.problem.ProblemBuilder
import glassbricks.factorio.recipes.problem.factory
import glassbricks.factorio.recipes.problem.stage
import glassbricks.recipeanalysis.Ingredient
import glassbricks.recipeanalysis.div
import glassbricks.recipeanalysis.lp.LpOptions
import glassbricks.recipeanalysis.lp.OrToolsLp
import glassbricks.recipeanalysis.recipelp.MultiStageProductionLp
import glassbricks.recipeanalysis.recipelp.runningFor
import kotlin.math.pow
import kotlin.time.Duration.Companion.minutes

fun main(): Unit = with(SpaceAge) {
    val vulcanusFactory = factory {
        vulcanusMachines()
        machines {
//            electromagneticPlant { integralCost() }
        }
        researchConfig = ResearchConfig(
            miningProductivity = 0.2,
            recipeProductivity = mapOf(RecipeID(castingLowDensityStructure.prototype.name) to 0.2)
        )
        recipes {
            default { allQualities() }
            allCraftingRecipes()
            calciteMining()
            coalMining {
                cost = 20.0
            }
            tungstenOreMining()

            remove(electromagneticPlant)
            remove(lightningCollector)
            remove(lightningRod)
            remove(teslagun)
            // manually remove weird recipes that it tries to do 0.002 of
            // which might be a tiny bit more optimal, but too complicated to be worth it
            remove(splitter)
            remove(transportBelt)
            remove(holmiumPlate)
            remove(holmiumPlateRecycling)
            remove(supercapacitorRecycling)
            remove(superconductorRecycling)
        }
    }

    fun ProblemBuilder.CostsScope.machineCosts() {
        costOf(assemblingMachine2, 1.0 + 1.0)
        costOf(assemblingMachine3, 3 + 1.0)
        costOf(foundry, 5 + 2.0)
        costOf(beacon, 4 + 1.0)
        costOf(bigMiningDrill, 5 + 2)
        costOf(recycler, 20)
        costOf(electromagneticPlant, 100)
    }

    val mallRunTime = 30.minutes
    val targetTime = 30.minutes
    val stage1 = vulcanusFactory.stage("modules") {
        input(lava, cost = 0.0)
        input(sulfuricAcid, cost = 0.0005)
        limit(holmiumOre, 500.0 / mallRunTime)
        limit(holmiumPlate.withQuality(epic), 100.0 / mallRunTime)

        for (module in module1s) {
            optionalOutput(module)
        }
        for (module in module2s + module3s) {
            optionalOutput(module)
            optionalOutput(module.withQuality(rare))
            optionalOutput(module.withQuality(epic))
//            if (module == qualityModule3) {
//                output(module.withQuality(legendary), rate = 4.0 / targetTime)
//            } else {
            optionalOutput(module.withQuality(legendary))
//            }
        }

        costs {
            machineCosts()
            for (module in module1s) {
                costOf(module, 1.0)
            }
            // make higher on purpose for now
            val qualityMultiplierCost = 6.0
            for ((i, q) in qualities.take(3).withIndex()) {
                for (module in module2s) {
                    costOf(module.withQuality(q), 5.0 * qualityMultiplierCost.pow(i))
                }
            }
            forbidUnspecifiedModules()
        }
    }

    val finalProduction = vulcanusFactory.stage("final production") {
        input(lava, cost = 0.0)
        input(sulfuricAcid, cost = 0.0005)
        limit(holmiumOre, 500.0 / targetTime)
        limit(holmiumPlate.withQuality(epic), 1000.0 / targetTime)
        limit(holmiumPlate.withQuality(legendary), 100.0 / targetTime)

        oneFullLegendaryMechArmor(targetTime)

        costs {
            machineCosts()
            for (module in module123AllQualities) {
                module producedBy stage1.runningFor(mallRunTime)
            }
        }
    }

    val problem = MultiStageProductionLp(
        stages = listOf(stage1, finalProduction)
    )

    val result = problem.solve(
        OrToolsLp(), LpOptions(
            timeLimit = 20.minutes,
            numThreads = Runtime.getRuntime().availableProcessors() - 2,
            enableLogging = true,
            epsilon = 1e-5
        )
    )
    println("Status: ${result.status}")
    result.solutions?.let {
        printAndExportStagedSolution("output/legendary-armor-staged", it, object : FactorioGraphExportFormatter {
            override fun formatIngredientRate(ingredient: Ingredient, rate: Double): String =
                "%.2f".format(rate * 60) + "/min"
        })
    }
}
