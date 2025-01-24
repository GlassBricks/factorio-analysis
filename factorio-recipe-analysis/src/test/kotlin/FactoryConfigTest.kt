package glassbricks.factorio.recipes

import glassbricks.factorio.recipes.problem.factory
import glassbricks.recipeanalysis.Symbol
import glassbricks.recipeanalysis.vector
import glassbricks.recipeanalysis.vectorWithUnits
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
                }
                "assembling-machine-2" {
                    qualities += uncommon
                    moduleConfig(fill = prod2)
                }
            }
            recipes {
                default {
                    cost = 1.2
                }
                "advanced-circuit" {
                    qualities.clear()
                    qualities += uncommon
                    qualities += rare
                    upperBound = 1.3
                }
                "transport-belt" {
                    integral = true
                }
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
        ).flatMap { listOf(it, it.withQuality(uncommon)) }
        val expectedRecipes = machines.flatMap { machine ->
            recipes.mapNotNull { machine.processingOrNull(it) }
        }.toSet()
        val allProcesses = config.allProcesses
        allProcesses.forAll {
            it.cost shouldBe 1.2
            val recipe = (it.process as MachineSetup<*>).process
            it.upperBound shouldBe if ((recipe as? Recipe)?.prototype == advCircuit.prototype) 1.3 else Double.POSITIVE_INFINITY
            it.integral shouldBe (recipe == transportBelt)
        }
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
                    additionalCosts = vectorWithUnits(symbolA to 1.0)
                }
            }
            recipes {
                "advanced-circuit" {
                    additionalCosts = vectorWithUnits(symbolB to 2.0)
                }
            }
        }
        val recipe = config.allProcesses.single()
        recipe.additionalCosts shouldBe vectorWithUnits(symbolA to 1.0, symbolB to 2.0)
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
                    additionalCosts = vectorWithUnits(symbol1 to 1.0)
                }
            }
        }
        val recipe = config.allProcesses.single()
        recipe.additionalCosts shouldBe vector(
            SpaceAge.itemOfOrNull(asm2) to 1.0,
            speed2 to 2.0,
            symbol1 to 1.0
        )
    }
}), WithFactorioPrototypes {
    override val prototypes: FactorioPrototypes get() = SpaceAge
}
