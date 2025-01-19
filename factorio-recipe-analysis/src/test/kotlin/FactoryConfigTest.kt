package glassbricks.factorio.recipes

import glassbricks.recipeanalysis.Symbol
import glassbricks.recipeanalysis.amountVector
import glassbricks.recipeanalysis.vector
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe

class FactoryConfigKtTest : FunSpec({
    val asm2 = machine("assembling-machine-2")
    val speed1 = module("speed-module")
    val speed2 = module("speed-module-2")
    val prod2 = module("productivity-module-2")
    val uncommon = SpaceAge.qualitiesMap["uncommon"]!!
    val rare = SpaceAge.qualitiesMap["rare"]!!
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
        ).flatMap { listOf(it, it.withMachineQuality(uncommon)) }
        val expectedRecipes = machines.flatMap { machine ->
            recipes.mapNotNull { machine.craftingOrNull(it) }
        }.toSet()
        val allProcesses = config.allProcesses
        allProcesses.forAll {
            it.cost shouldBe 1.2
            val recipe = (it.process as CraftingProcess).recipe
            it.upperBound shouldBe if (recipe.prototype == advCircuit.prototype) 1.3 else Double.POSITIVE_INFINITY
            it.integral shouldBe (recipe == transportBelt)
        }
        val actualRecipes = allProcesses.mapTo(mutableSetOf()) { it.process as CraftingProcess }
        val extra = expectedRecipes - actualRecipes
        val missing = actualRecipes - expectedRecipes
        assertSoftly {
            extra shouldBe emptySet()
            missing shouldBe emptySet()
        }
    }
    test("additional costs") {
        val symbolA = Symbol("a")
        val symbolB = Symbol("b")
        val config = SpaceAge.factory {
            machines {
                "assembling-machine-2" {
                    additionalCosts = vector(symbolA to 1.0)
                }
            }
            recipes {
                "advanced-circuit" {
                    additionalCosts = vector(symbolB to 2.0)
                }
            }
        }
        val recipe = config.allProcesses.single()
        recipe.additionalCosts shouldBe vector(symbolA to 1.0, symbolB to 2.0)
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
                    additionalCosts = vector(symbol1 to 1.0)
                }
            }
        }
        val recipe = config.allProcesses.single()
        recipe.additionalCosts shouldBe amountVector(
            SpaceAge.itemOf(asm2) to 1.0,
            speed2 to 2.0,
            symbol1 to 1.0
        )
    }
})
