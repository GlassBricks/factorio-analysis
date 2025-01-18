package glassbricks.factorio.recipes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FactoryConfigKtTest : FunSpec({
    test("machine with quality and modules") {
        val asm2 = machine("assembling-machine-2")
        val speed1 = module("speed-module")
        val speed2 = module("speed-module-2")
        val prod2 = module("productivity-module-2")
        val uncommon = SpaceAge.qualitiesMap["uncommon"]!!
        val rare = SpaceAge.qualitiesMap["rare"]!!
        val config = factoryConfig(SpaceAge) {
            machines {
                default {
                    emptyModuleConfig()
                    moduleConfig(speed1, fill = speed2)
                }
                "assembling-machine-2" {
                    qualities += uncommon
                    moduleConfig(fill = prod2)
                }
            }
            recipes {
                default {}
                "advanced-circuit" {
                    qualities.clear()
                    qualities += uncommon
                    qualities += rare
                }
                "transport-belt"()
            }
        }
        val advCircuit = recipe("advanced-circuit")
        val transportBelt = recipe("transport-belt")
        val recipes = listOf(
            advCircuit.withQuality(uncommon),
            advCircuit.withQuality(rare),
            transportBelt
        )
        val machines = listOf(
            asm2,
            asm2.withModules(speed1, fill = speed2),
            asm2.withModules(fill = prod2),
        ).flatMap { listOf(it, it.withMachineQuality(uncommon)) }
        val expectedRecipes = machines.flatMap { machine ->
            recipes.mapNotNull { machine.craftingOrNull(it) }
        }.toSet()
        val craftingSet = config.allCraftingSetups.toSet()
        val diff1 = expectedRecipes - craftingSet
        val diff2 = craftingSet - expectedRecipes
        diff1 shouldBe emptySet()
        diff2 shouldBe emptySet()
    }
})
