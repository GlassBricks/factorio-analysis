package scripts.vulcanus

import glassbricks.factorio.recipes.Module
import glassbricks.factorio.recipes.SpaceAge
import glassbricks.factorio.recipes.WithBeaconCount
import glassbricks.factorio.recipes.problem.FactoryConfigBuilder
import glassbricks.factorio.recipes.problem.ProblemBuilder
import scripts.*

val defaultVulcanusBeaconConfig = with(SpaceAge) {
    listOf(speedModule2, speedModule3)
        .flatMap { beaconsWithSharing(it) }
        .map { listOf(it) }
}

fun FactoryConfigBuilder.vulcanusMachines(
    modules: List<Module> = nonEffModulesAllQualities,
    beacons: List<List<WithBeaconCount>> = defaultVulcanusBeaconConfig,
) {
    machines {
        default {
            includeBuildCosts()
            includePowerCosts()
            for (module in modules) {
                moduleConfig(fill = module)
                if (module.effects.quality <= 0) {
                    for (beaconConfig in beacons) {
                        moduleConfig(beacons = beaconConfig)
                        moduleConfig(fill = module, beacons = beaconConfig)
                    }
                }
            }
        }
        assemblingMachine3()
        assemblingMachine2()
        chemicalPlant()
        oilRefinery()
        foundry()
        bigMiningDrill()
        electricFurnace()
        recycler()
        electromagneticPlant()
    }
}

val vulcanusQualityMultipliers = listOf(
    // jump from normal->uncommon higher, because you can no longer use speed modules
    6.0,
    4.0,
    3.8,
    3.8
)

fun ProblemBuilder.CostsScope.vulcanusModuleCosts1() {
    for (module in module1s) {
        addQualityCosts(module, 1.0, vulcanusQualityMultipliers)
    }
    for (module in module2s) {
        addQualityCosts(module, 7.0 / 1.5, vulcanusQualityMultipliers)
    }
    addQualityCosts(speedModule3, (6.5 / 1.5) * 5 / 1.5, vulcanusQualityMultipliers)
    addQualityCosts(qualityModule3, (6.5 / 1.5) * 5 / 1.5 + 5.0, vulcanusQualityMultipliers)
}

fun ProblemBuilder.CostsScope.vulcanusMachineCosts1() {
    costOf(assemblingMachine2, 1)
    costOf(chemicalPlant, 1)
    costOf(oilRefinery, 2)
    costOf(assemblingMachine3, 2 + 4)
    costOf(foundry, 6)
    costOf(bigMiningDrill, 5 + 2)
    costOf(steamTurbine, 4)
    costOf(beacon, 4 / 1.5 + 1)
    costOf(electricFurnace, 1)
    // imported
    costOf(recycler, 30)
    costOf(electromagneticPlant, 200)
}
