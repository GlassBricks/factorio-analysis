import glassbricks.factorio.prototypes.RecipeID
import glassbricks.factorio.recipes.ResearchConfig
import glassbricks.factorio.recipes.SpaceAge
import glassbricks.factorio.recipes.problem.ProblemBuilder
import glassbricks.factorio.recipes.problem.factory
import glassbricks.factorio.recipes.problem.stage
import glassbricks.recipeanalysis.*
import glassbricks.recipeanalysis.lp.LpOptions
import glassbricks.recipeanalysis.lp.OrToolsLp
import glassbricks.recipeanalysis.recipelp.MultiStageProductionLp
import glassbricks.recipeanalysis.recipelp.runningFor
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

fun main(): Unit = with(SpaceAge) {
    val vulcanusFactory = factory {
        vulcanusMachines()
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
        }
    }

    fun ProblemBuilder.CostsScope.machineCosts() {
        costOf(assemblingMachine3, 3 + 1.0)
        costOf(foundry, 5 + 2.0)
        costOf(beacon, 4 + 1.0)
        costOf(bigMiningDrill, 5 + 2)
        costOf(recycler, 20)
        costOf(electromagneticPlant, 100)
    }

    val stage1 = vulcanusFactory.stage("modules") {
        input(lava, cost = 0.0)
        input(sulfuricAcid, cost = 0.0005)
//        input(holmiumOre, limit = 500.0 / 30.minutes)
//        input(holmiumPlate.withQuality(epic), limit = 300.0 / 30.minutes)
        for (q in qualities) {
            input(superconductor.withQuality(q), cost = 0.0)
        }

        for (module in module12sAllQualities) {
            optionalOutput(module)
        }
        costs {
            machineCosts()
            for (module in module1s) {
                costOf(module, 1.0)
            }
            for (module in module2s) {
                costOf(module, 5.0)
            }
            forbidUnspecifiedModules()
        }
    }

    val targetTime = 0.5.hours.asTime()
    val finalProduction = vulcanusFactory.stage("final production") {
        input(lava, cost = 0.0)
        input(sulfuricAcid, cost = 0.0005)
        limit(holmiumOre, 500.0 / targetTime)
        limit(holmiumPlate.withQuality(epic), 1000.0 / targetTime)
        limit(holmiumPlate.withQuality(legendary), 100.0 / targetTime)

        oneFullLegendaryMechArmor(targetTime)

        costs {
            machineCosts()
            for (module in module12sAllQualities) {
                module producedBy stage1.runningFor(30.minutes)
            }
        }
    }

    val problem = MultiStageProductionLp(
        stages = listOf(stage1, finalProduction)
    )

    val result = problem.solve(
        OrToolsLp(), LpOptions(
            timeLimit = 10.minutes,
            numThreads = Runtime.getRuntime().availableProcessors() - 2,
            enableLogging = true,
            epsilon = 1e-5
        )
    )
    println("Status: ${result.status}")
    val solution = result.solutions ?: return
    printAndExportStagedSolution("output/legendary-armor-staged", solution)
}
