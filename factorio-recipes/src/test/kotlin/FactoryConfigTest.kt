package glassbricks.factorio.recipes

import glassbricks.factorio.recipes.problem.FactoryConfigBuilder
import glassbricks.factorio.recipes.problem.factory
import glassbricks.recipeanalysis.Symbol
import glassbricks.recipeanalysis.lp.VariableType
import glassbricks.recipeanalysis.plus
import glassbricks.recipeanalysis.vectorOf
import glassbricks.recipeanalysis.vectorOfWithUnits
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe

class FactoryConfigKtTest : FunSpec({
    this as FactoryConfigKtTest
    val asm2 = craftingMachine("assembling-machine-2")
    val asm3 = craftingMachine("assembling-machine-3")
    val speed1 = module("speed-module")
    val speed2 = module("speed-module-2")
    val prod2 = module("productivity-module-2")
    val uncommon = SpaceAge.qualityMap["uncommon"]!!
    val rare = SpaceAge.qualityMap["rare"]!!
    fun factory(block: FactoryConfigBuilder.() -> Unit) = SpaceAge.factory { block() }
        .also {
            it.getAllProcesses().forEach {
                val process = it.process as MachineProcess<*>
                process.machine.canProcess(process.recipe) shouldBe true
            }
        }

    test("does not insert prod modules where not allowed") {
        factory {
            machines {
                asm2 {
                    moduleConfig(prod2)
                }
            }
            recipes {
                "transport-belt" {}
                "advanced-circuit" {}
            }
        }
        // assertion happens in [factory]
    }
    test("machine, machine quality, modules, recipe quality") {
        val config = factory {
            machines {
                default {
                    moduleConfig(speed1, fill = speed2)
                    cost = 1.2
                }
                "assembling-machine-2" {
                    qualities += uncommon
                    moduleConfig(fill = prod2)
                    integral()
                    upperBound = 1.3
                }
            }
            recipes {
                default {}
                "advanced-circuit" {
                    qualities = setOf(uncommon, rare)
                }
                "transport-belt" {}
            }
        }
        val advCircuit = recipe("advanced-circuit")
        val allProcesses = config.getAllProcesses()
        allProcesses.forAll {
            it.variableConfig.cost shouldBe 1.2
            var process = it.process as MachineProcess<*>
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
            recipes.mapNotNull { machine.craftingOrNull(it)?.toProcess() }
        }.toSet()

        val actualRecipes = allProcesses.mapTo(mutableSetOf()) { it.process as MachineProcess<*> }
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
        val recipe = config.getAllProcesses().single().process as MachineProcess<*>
        recipe shouldBe drill.crafting(ironOre).toProcess()
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
        val recipe = config.getAllProcesses().single()
        recipe.additionalCosts.toMap() shouldBe mapOf(symbolA to 1.0, symbolB to 2.0)
    }
    test("build costs") {
        val symbol1 = Symbol("1")
        val config = SpaceAge.factory {
            includeBuildCosts()
            machines {
                asm2 {
                    noEmptyModules()
                    moduleConfig(fill = speed2)
                }
            }
            recipes {
                "advanced-circuit" {
                    additionalCosts = vectorOfWithUnits(symbol1 to 1.0)
                }
            }
        }
        val recipe = config.getAllProcesses().single()
        recipe.additionalCosts shouldBe vectorOf(
            SpaceAge.itemOfOrNull(asm2) to 1.0,
            speed2 to 2.0,
            symbol1 to 1.0
        )
    }

    test("machine count") {
        val config = SpaceAge.factory {
            includeMachineCount()
            machines {
                asm2 {
                    noEmptyModules()
                    moduleConfig(fill = speed2)
                }
            }
            recipes {
                "advanced-circuit"()
            }
        }
        val recipe = config.getAllProcesses().single()
        recipe.additionalCosts shouldBe vectorOf(
            MachineSymbol(
                asm2.withModules(fill = speed2)
            ) to 1.0
        )
    }

    test("setup config") {
        val config = SpaceAge.factory {
            setups {
                (machine("assembling-machine-2").crafting(recipe("advanced-circuit"))) {
                    cost += 10.0
                    additionalCosts += vectorOf(item("iron-plate") to 1.0)
                }
            }
        }
        val recipe = config.getAllProcesses().single()
        recipe.variableConfig.cost shouldBe 10.0
        recipe.additionalCosts shouldBe vectorOf(item("iron-plate") to 1.0)
    }
    test("extraConfig") {
        val config = SpaceAge.factory {
            machines.addConfig(machine("assembling-machine-2"))
            recipes.addConfig(recipe("advanced-circuit"))
            recipes.addConfig(recipe("electronic-circuit"))
            extraConfigBeforeQuality {
                cost += if (setup.recipe == recipe("advanced-circuit")) 10.0 else 20.0
            }
        }
        val recipe = config.getAllProcesses()
        val rcProcess = recipe.single { (it.process as MachineProcess<*>).recipe == recipe("advanced-circuit") }
        rcProcess.variableConfig.cost shouldBe 10.0
        val gcProcess = recipe.single { (it.process as MachineProcess<*>).recipe == recipe("electronic-circuit") }
        gcProcess.variableConfig.cost shouldBe 20.0
    }
    test("machine filters") {
        val config = SpaceAge.factory {
            machines {
                asm2 {
                    onlyRecipes(recipe("advanced-circuit"))
                }
            }
            recipes {
                "advanced-circuit" {}
                "electronic-circuit" {}
            }
        }
        val recipe = config.getAllProcesses().single().process as MachineProcess<*>
        recipe.recipe shouldBe recipe("advanced-circuit")
    }
    test("recipe filters") {
        val config = SpaceAge.factory {
            machines {
                asm2()
                asm3()
            }
            recipes {
                "advanced-circuit" {
                    onlyUsing(asm3)
                }
            }
        }
        val recipe = config.getAllProcesses().single().process as MachineProcess<*>
        recipe.machine shouldBe asm3
    }
    test("factory filters") {
        val config = SpaceAge.factory {
            machines {
                asm2()
                asm3()
            }
            recipes {
                "advanced-circuit" {}
                "electronic-circuit" {}
            }
            filters += { machine, recipe ->
                machine == asm3 && recipe == recipe("advanced-circuit")
            }
        }
        val recipe = config.getAllProcesses().single().process as MachineProcess<*>
        recipe.machine shouldBe asm3
        recipe.recipe shouldBe recipe("advanced-circuit")
    }
}), FactorioPrototypesScope {
    override val prototypes: FactorioPrototypes get() = SpaceAge
}
