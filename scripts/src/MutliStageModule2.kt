import glassbricks.factorio.recipes.ResearchConfig
import glassbricks.factorio.recipes.SpaceAge
import glassbricks.factorio.recipes.invoke
import glassbricks.factorio.recipes.problem.factory
import glassbricks.factorio.recipes.problem.stage
import glassbricks.recipeanalysis.lp.LpOptions
import glassbricks.recipeanalysis.perMinute
import glassbricks.recipeanalysis.recipelp.MultiStageProductionLp
import glassbricks.recipeanalysis.recipelp.runningFor
import kotlin.time.Duration.Companion.minutes

fun main() = with(SpaceAge) {
    val vulcanusMachines = listOf(
        bigMiningDrill,
        assemblingMachine2,
        assemblingMachine3,
        foundry,
        chemicalPlant,
        oilRefinery,
        electricFurnace,
    )
    val vulcanusEntities = vulcanusMachines + beacon
    val vulcImportedMachines = listOf(
        recycler,
        electromagneticPlant
    )
    val allMachines = vulcanusMachines + vulcImportedMachines

    fun vulcFactory(
        coalMiningCost: Double = 5000.0,
        tungstenMiningCost: Double = coalMiningCost * 2,
        researchConfig: ResearchConfig = ResearchConfig(miningProductivity = 0.2),
    ) = factory {
        machines {
            default {
                includeBuildCosts()
                moduleConfig() // no modules
                for (module in module12sAllQualities) {
                    moduleConfig(fill = module)
                    if (module.effects.quality <= 0) {
                        moduleConfig(fill = module, beacons = listOf(beacon(fill = speedModule2, sharing = 6.0)))
                    }
                }
            }
            for (machine in allMachines) {
                machine {}
            }
        }

        this.researchConfig = researchConfig
        recipes {
            default { allQualities() }
            allCraftingRecipes()
            calciteMining()
            coalMining {
                cost = coalMiningCost
            }
            tungstenOreMining {
                cost = tungstenMiningCost
            }
            // manually disable fulgora recipes for now
            remove(electromagneticPlant.item())
        }
    }

    // mall 1: just vulcanus machines
    val mall1 = vulcFactory().stage("mall1") {
        input(lava, cost = 0.0)
        input(sulfuricAcid, cost = 0.005)

        for (entity in vulcanusEntities) {
            optionalOutput(entity.item())
        }
        for (module in module1s) {
            optionalOutput(module)
        }
        costs {
            // not super accurate; roughly based off raw cost
            costOf(foundry, (30 + 250 + 20) / 1.5)
            costOf(bigMiningDrill, (70 + 140 + 30) / 1.5)
            costOf(assemblingMachine2, 55)
            costOf(chemicalPlant, 55)
            costOf(oilRefinery, 140)
            costOf(electricFurnace, 100)
            for (module in module1s) costOf(module, 50)
            forbidAllUnspecified()
        }
    }

    // mall2: just modules
    val mall2 = vulcFactory(100.0).stage("mall2") {
        input(lava, cost = 0.0)
        input(sulfuricAcid, cost = 0.005)

        val mall1Production = mall1.runningFor(30.minutes)

        for (module in module12sAllQualities) {
            optionalOutput(module)
        }
        costs {
            for (entity in vulcanusEntities) {
                entity.item() producedBy mall1Production
            }
            for (module in module12sAllQualities) {
                module producedBy mall1Production
            }
            limit(electromagneticPlant, 10.0)
            limit(recycler, 20.0)
            forbidAllUnspecified()
        }
    }

    // mall3: more quality-quality-modules. This time we get to import stuff!
    val mall3 = vulcFactory(50.0).stage("mall3") {
        input(lava, cost = 0.0)
        input(sulfuricAcid, cost = 0.005)

        output(qualityModule2.withQuality(epic), 8.perMinute)

        val mall12Production = mall1.runningFor(30.minutes) + mall2.runningFor(30.minutes)

        costs {
            for (entity in vulcanusEntities) {
                entity.item() producedBy mall12Production
            }
            for (module in module12sAllQualities) {
                module producedBy mall12Production
            }
            for (importedMachine in vulcImportedMachines) {
                costOf(importedMachine, 500.0)
            }
        }
    }

    val problem = MultiStageProductionLp(
        stages = listOf(mall1, mall2, mall3)
    )

    val result = problem.solve(
        options = LpOptions(
            numThreads = Runtime.getRuntime().availableProcessors() - 2,
            epsilon = 1e-5,
            enableLogging = true,
        )
    )

    println("Status: ${result.status}")
    val solution = result.solutions ?: return

    println("Objective: ${result.lpSolution!!.objectiveValue}")

    val pathName = "output/module2-staged"
    printAndExportStagedSolution(pathName, solution)
}
