package scripts.fulgora

import glassbricks.factorio.recipes.Module
import glassbricks.factorio.recipes.ResearchConfig
import glassbricks.factorio.recipes.SpaceAge
import glassbricks.factorio.recipes.WithBeaconCount
import glassbricks.factorio.recipes.problem.FactoryConfigBuilder
import glassbricks.factorio.recipes.problem.ProblemBuilder
import glassbricks.factorio.recipes.problem.factory
import scripts.*
import scripts.vulcanus.vulcanusMachines

val defaultFulgoraBeaconConfig = with(SpaceAge) {
    listOf(speedModule2, speedModule3)
        .flatMap { beaconsWithSharing(it) }
        .map { listOf(it) }
}

val fulgoraQualityMultipliers = listOf(
    4.0,
    3.8,
    3.8,
    3.8
)

fun FactoryConfigBuilder.fulgoraMachines(
    modules: List<Module> = nonEffModulesAllQualities
        .filter { it.prototype != prototypes.productivityModule3.prototype },
    beacons: List<List<WithBeaconCount>> = defaultFulgoraBeaconConfig,
) = with(prototypes) {
    // same machines, different cost
    vulcanusMachines(modules, beacons)
    machines {
        bigMiningDrill {
            moduleSetConfigs.removeIf {
                it.beacons.sumOf { it.beaconCount.count } >= 4
            }
        }
    }
//    machines {
//        default {
//            includeBuildCosts()
//            includePowerCosts()
//            for (module in modules) {
//                moduleConfig(fill = module)
//                if (module.effects.quality <= 0) {
//                    for (beaconConfig in beacons) {
//                        moduleConfig(beacons = beaconConfig)
//                        moduleConfig(fill = module, beacons = beaconConfig)
//                    }
//                }
//            }
//        }
//        assemblingMachine2()
//        assemblingMachine3()
//        chemicalPlant()
//        foundry()
//        bigMiningDrill()
//        electricFurnace()
//        recycler()
//        electricFurnace()
//    }
}

fun ProblemBuilder.CostsScope.fulgoraModuleCosts1() = with(prototypes) {
    val baseModuleCost = 5.0
    for (module in module1s) {
        addQualityCosts(module, baseModuleCost, fulgoraQualityMultipliers)
    }
    val module2Cost = baseModuleCost * 6 / 1.5
    for (module in module2s) {
        addQualityCosts(module, module2Cost, fulgoraQualityMultipliers)
    }
    addQualityCosts(speedModule3, module2Cost * 4.5 / 1.5 + 5.0, fulgoraQualityMultipliers)
    addQualityCosts(qualityModule3, module2Cost * 4.5 / 1.5, fulgoraQualityMultipliers)
}

fun ProblemBuilder.CostsScope.fulgoraMachineCosts1() = with(prototypes) {
    costOf(assemblingMachine2, 10.0)
    costOf(assemblingMachine3, 40.0)
    costOf(chemicalPlant, 10.0)
//    costOf(oilRefinery, 15.0)
    costOf(foundry, 400)
    costOf(bigMiningDrill, 0) // cost to be set by outside instead
    costOf(electricFurnace, 10.0)
    costOf(beacon, 25.0)
    costOf(recycler, 30.0)
    costOf(electromagneticPlant, 100.0)
}

fun fulgoraFactory1(scrapCost: Number, config: FactoryConfigBuilder.() -> Unit = {}) = SpaceAge.factory {
    fulgoraMachines()
    recipes {
        default {
            allQualities()
            cost = 0.0
        }
        allCraftingRecipes()
        this@factory.prototypes.scrapMining {
            cost = scrapCost.toDouble()
        }
    }
    researchConfig = ResearchConfig(
        miningProductivity = 0.2
    )
    config()
}

fun ProblemBuilder.fulgoraConfig1() = with(prototypes) {
    input(heavyOil, cost = 0.0)
    costs {
        fulgoraMachineCosts1()
        fulgoraModuleCosts1()
        forbidAllUnspecified()
    }
}
