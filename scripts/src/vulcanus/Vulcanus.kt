package scripts.vulcanus

import glassbricks.factorio.recipes.Module
import glassbricks.factorio.recipes.WithBeaconCount
import glassbricks.factorio.recipes.problem.FactoryConfigBuilder
import glassbricks.factorio.recipes.problem.ProblemBuilder
import scripts.*

fun FactoryConfigBuilder.vulcanusMachines(
    modules: List<Module> = module123AllQualities,
    beacons: List<List<WithBeaconCount>> = speed2Beacons.map { listOf(it) },
) {
    machines {
        default {
            includeBuildCosts()
            for (module in modules) {
                moduleConfig(fill = module)
                if (module.effects.quality <= 0) {
                    for (beaconConfig in beacons) {
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
    4.5,
    4.3,
    4.0
)

fun ProblemBuilder.CostsScope.vulcanusCosts1() {
    vulcanusMachineCosts1()
    vulcanusModuleCosts1()
}

fun ProblemBuilder.CostsScope.vulcanusModuleCosts1() {
    for (module in module1s) {
        addQualityCosts(module, 1.0, vulcanusQualityMultipliers)
    }
    for (module in module2s) {
        addQualityCosts(module, 6.5 / 1.5, vulcanusQualityMultipliers)
    }
    addQualityCosts(speedModule3, (6.5 / 1.5) * 5 / 1.5, vulcanusQualityMultipliers)
    addQualityCosts(qualityModule3, (6.5 / 1.5) * 5 / 1.5 + 3.0, vulcanusQualityMultipliers)
}

fun ProblemBuilder.CostsScope.vulcanusMachineCosts1() {
    // note: default cost of 1.0 on recipes can be thought of as "space" cost
    costOf(assemblingMachine2, 1)
    costOf(assemblingMachine3, 3)
    costOf(foundry, 5 + 1.0) // +1 for extra space cost
    costOf(bigMiningDrill, 5)
    costOf(beacon, 4 / 1.5 + 1.0)
    // imported
    costOf(recycler, 20)
    costOf(electromagneticPlant, 200)
}
