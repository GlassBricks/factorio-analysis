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
) = with(prototypes) {
    includeBuildCosts()
    machines {
        default {
            moduleConfigWithBeacons(modules, beacons)
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

fun ProblemBuilder.CostsScope.vulcanusModuleCosts1() = with(prototypes) {
    val baseModuleCost = 1.0
    for (module in module1s) {
        addQualityCosts(module, baseModuleCost, vulcanusQualityMultipliers)
    }
    val module2Cost = baseModuleCost * 7 / 1.5
    for (module in module2s) {
        addQualityCosts(module, module2Cost, vulcanusQualityMultipliers)
    }
    addQualityCosts(speedModule3, module2Cost * 5 / 1.5, vulcanusQualityMultipliers)
    addQualityCosts(qualityModule3, module2Cost * 5 / 1.5 + 5.0, vulcanusQualityMultipliers)
}

fun ProblemBuilder.CostsScope.vulcanusMachineCosts1() = with(prototypes) {
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
    costOf(recycler, 50)
    costOf(electromagneticPlant, 200)
}
