package glassbricks.factorio.recipes

import glassbricks.factorio.recipes.problem.factory
import glassbricks.recipeanalysis.Symbol
import glassbricks.recipeanalysis.lp.VariableType
import glassbricks.recipeanalysis.vectorOf
import glassbricks.recipeanalysis.vectorOfWithUnits
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe

class FactoryConfigKtTest : FunSpec({
    this as FactoryConfigKtTest
    val asm2 = craftingMachine("assembling-machine-2")
    val speed1 = module("speed-module")
    val speed2 = module("speed-module-2")
    val prod2 = module("productivity-module-2")
    val uncommon = SpaceAge.qualityMap["uncommon"]!!
    val rare = SpaceAge.qualityMap["rare"]!!
    test("machine, machine quality, modules, recipe quality") {
        val config = SpaceAge.factory {
            machines {
                default {
                    emptyModuleConfig()
                    moduleConfig(speed1, fill = speed2)
                    cost = 1.2
                }
                "assembling-machine-2" {
                    qualities += uncommon
                    moduleConfig(fill = prod2)
                    type = VariableType.Integer
                    upperBound = 1.3
                }
            }
            recipes {
                default {
                }
                "advanced-circuit" {
                    qualities.clear()
                    qualities += uncommon
                    qualities += rare
                }
                "transport-belt" {}
            }
        }
        val advCircuit = recipe("advanced-circuit")
        val allProcesses = config.allProcesses
        allProcesses.forAll {
            it.variableConfig.cost shouldBe 1.2
            var process = it.process as MachineSetup<*>
            it.variableConfig.upperBound shouldBe if (process.machine.prototype == asm2.prototype) 1.3 else Double.POSITIVE_INFINITY
            it.variableConfig.type shouldBe if (process.machine.prototype == asm2.prototype) VariableType.Integer else VariableType.Continuous
        }

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
        ).flatMap { listOf(it, it.withQuality(uncommon)) }
        val expectedRecipes = machines.flatMap { machine ->
            recipes.mapNotNull { machine.processingOrNull(it) }
        }.toSet()

        val actualRecipes = allProcesses.mapTo(mutableSetOf()) { it.process as MachineSetup<*> }
        val extra = expectedRecipes - actualRecipes
        val missing = actualRecipes - expectedRecipes
        assertSoftly {
            extra shouldBe emptySet()
            missing shouldBe emptySet()
        }
    }
    test("mining recipe") {
        val ironOre = resource("iron-ore")
        val drill = miningDrill("electric-mining-drill")
        val config = SpaceAge.factory {
            machines {
                drill()
            }
            recipes {
                ironOre {}
            }
        }
        val recipe = config.allProcesses.single().process as MachineSetup<*>
        recipe shouldBe drill.processing(ironOre)

    }
    test("additional costs") {
        val symbolA = Symbol("a")
        val symbolB = Symbol("b")
        val config = SpaceAge.factory {
            machines {
                "assembling-machine-2" {
                    additionalCosts = vectorOfWithUnits(symbolA to 1.0)
                }
            }
            recipes {
                "advanced-circuit" {
                    additionalCosts = vectorOfWithUnits(symbolB to 2.0)
                }
            }
        }
        val recipe = config.allProcesses.single()
        recipe.additionalCosts shouldBe vectorOfWithUnits(symbolA to 1.0, symbolB to 2.0)
    }
    test("build costs") {
        val symbol1 = Symbol("1")
        val config = SpaceAge.factory {
            machines {
                default { includeBuildCosts = true }
                asm2 {
                    moduleConfig(fill = speed2)
                }
            }
            recipes {
                "advanced-circuit" {
                    additionalCosts = vectorOfWithUnits(symbol1 to 1.0)
                }
            }
        }
        val recipe = config.allProcesses.single()
        recipe.additionalCosts shouldBe vectorOf(
            SpaceAge.itemOfOrNull(asm2) to 1.0,
            speed2 to 2.0,
            symbol1 to 1.0
        )
    }
}), WithFactorioPrototypes {
    override val prototypes: FactorioPrototypes get() = SpaceAge
}
