package scripts.gleba

import glassbricks.factorio.recipes.Module
import glassbricks.factorio.recipes.WithBeaconCount
import glassbricks.factorio.recipes.problem.FactoryConfigBuilder
import scripts.assemblingMachine2
import scripts.biochamber
import scripts.electricMiningDrill
import scripts.moduleConfigWithBeacons

fun FactoryConfigBuilder.glebaMachines(
    modules: List<Module>,
    beacons: List<List<WithBeaconCount>>,
) = with(prototypes) {
    machines {
        default {
            moduleConfigWithBeacons(modules, beacons)
        }
        assemblingMachine2()
        electricMiningDrill()
        biochamber()
    }
}
