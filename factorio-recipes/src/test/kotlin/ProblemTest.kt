package glassbricks.factorio.recipes
/*

import glassbricks.factorio.recipes.problem.factory
import glassbricks.factorio.recipes.problem.problem
import glassbricks.recipeanalysis.*
import glassbricks.recipeanalysis.lp.LpOptions
import glassbricks.recipeanalysis.lp.LpResultStatus
import glassbricks.recipeanalysis.lp.VariableType
import glassbricks.recipeanalysis.recipelp.MultiStageProductionLp
import glassbricks.recipeanalysis.recipelp.toStage
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe

class ProblemTest : FunSpec({
    this as ProblemTest
    val gc = item("electronic-circuit")
    val ironPlate = item("iron-plate")
    val copperPlate = item("copper-plate")
    val ironGearWheel = item("iron-gear-wheel")
    val speed1 = module("speed-module")
    val speed2 = module("speed-module-2")
    val prod3 = module("productivity-module-2")
    val qual3 = module("quality-module-3")

    val (_, _, rare, epic, legendary) = prototypes.qualities

    val gcFactory by lazy {
        factory {
            machines {
                "assembling-machine-2" {}
            }
            recipes {
                "copper-cable" {}
                (gc.prototype.name) {}
                (ironGearWheel.prototype.name) {}
                // something irrelevant
                "iron-chest" {}
            }
        }
    }
    test("solve maximize") {
        val problem = problem {
            factory = gcFactory
            limit(copperPlate, 3.perSecond)
            limit(ironPlate, 3.perSecond)
            maximize(gc)
        }
        val solution = problem.solve().solution!!
        Rate(solution.outputs[gc]) shouldBe 2.perSecond
        Rate(solution.inputs[ironPlate]) shouldBe 2.perSecond
        Rate(solution.inputs[copperPlate]) shouldBe 3.perSecond
    }
    test("solve limit") {
        val problem = problem {
            factory = gcFactory
            input(copperPlate)
            input(ironPlate)
            output(gc, 2.perSecond)
        }
        val solution = problem.solve().solution!!
        Rate(solution.outputs[gc]) shouldBe 2.perSecond
        Rate(solution.inputs[ironPlate]) shouldBe 2.perSecond
        Rate(solution.inputs[copperPlate]) shouldBe 3.0.perSecond
    }

    test("basic gambling") {
        val basicGamblingFactory = factory {
            machines {
                default {
                    moduleConfig(fill = qual3)
                    emptyModuleConfig()
                }
                "assembling-machine-3" {}
                "recycler" {}
            }
            recipes {
                default { allQualities() }
                "iron-chest" {}
                "iron-chest-recycling" {}
            }
        }
        val problem = problem {
            factory = basicGamblingFactory
            input(ironPlate)
            output(ironPlate.withQuality(legendary), 6.perMinute)
        }
        val solution = problem.solve()
        solution.status shouldBe LpResultStatus.Optimal
        println(solution.solution!!.processes.display())
    }

    test("can cast pipe") {
        val problem = problem {
            factory {
                machines {
                    "foundry" {}
                }
                recipes {
                    "casting-pipe" {}
                    "casting-iron" {}
                    "casting-pipe-to-ground" {}
                }
            }
            input(fluid("molten-iron"))
            output(item("pipe-to-ground"), 1.perSecond)
        }
        val solution = problem.solve()
        solution.status shouldBe LpResultStatus.Optimal
        println(solution.solution!!.processes.display())
    }
    test("can have excess") {
        val problem = problem {
            factory {
                machines {
                    "recycler" {}
                }
                recipes {
                    "pipe-to-ground-recycling" {}
                }
            }
            input(item("pipe-to-ground"))
            output(ironPlate, 1.perSecond)
        }
        val solution = problem.solve()
        solution.status shouldBe LpResultStatus.Optimal
        println(solution.solution!!.processes.display())
    }

    test("underground pipes casting is better") {
        val qual2 = module("quality-module-2")
        val gamblingFactory = factory {
            machines {
                default {
                    emptyModuleConfig()
                    moduleConfig(fill = qual2)
                    moduleConfig(fill = prod3, beacons = listOf(beacon(fill = speed2)))
                    moduleConfig(fill = speed1, beacons = listOf(beacon(fill = speed2)))
                }
                "assembling-machine-3" {}
                "foundry" {}
                "recycler" {}
            }
            recipes {
                default {
                    allQualities()
                }
                "casting-iron" {}
                "casting-pipe-to-ground" {}
                "casting-pipe" {}
                "iron-chest" {}
                "pipe-to-ground" {}
                "pipe" {}

                allRecycling()
            }
        }
        val problem = problem {
            factory = gamblingFactory
            val moltenIron = fluid("molten-iron")
            input(moltenIron)
            output(ironPlate.withQuality(rare), 6.perMinute)
        }
        val solution = problem.solve()
        solution.status shouldBe LpResultStatus.Optimal
        println(solution.solution!!.processes.display())
    }

    test("constrain machines") {
        val problem = problem {
            factory {
                machines {
                    "assembling-machine-1" {
                        includeBuildCosts()
                    }
                }
                recipes {
                    (ironGearWheel.prototype.name) {}
                }
            }
            input(ironPlate)
            maximize(ironGearWheel)
            costs {
                limit(item("assembling-machine-1"), 1.0)
            }
        }
        val solution = problem.solve().solution!!

        val inputRate = Rate(solution.inputs[ironPlate])
        inputRate shouldBe 2.perSecond
        val outputRate = Rate(solution.outputs[ironGearWheel])
        outputRate shouldBe 1.perSecond

        val recipeUsage = solution.processes.values.single()
        recipeUsage shouldBe 1.0
    }

    test("constrain modules") {
        val problem = problem {
            factory {
                machines {
                    "assembling-machine-2" {
                        includeBuildCosts = true
                        moduleConfig()
                        moduleConfig(fill = prod3)
                    }
                }
                recipes {
                    (ironGearWheel.prototype.name) {}
                }
            }
            input(ironPlate)
            maximize(ironGearWheel)
            costs {
                limit(prod3, 2.0)
                limit(item("assembling-machine-2"), 1.0)
            }
        }
        val solution = problem.solve()
        solution.status shouldBe LpResultStatus.Optimal
        println(solution.solution!!.processes.display())
    }

    test("cost on symbol") {
        val foo = Symbol("foo")
        val problem = problem {
            factory {
                machines {
                    default {
                        includeBuildCosts = true
                    }
                    "assembling-machine-2" {
                        additionalCosts += vectorWithUnits(foo to 2.0)
                    }
                    "assembling-machine-1" {
                        additionalCosts += vectorWithUnits(foo to 1.0)
                    }
                }
                recipes {
                    (ironGearWheel.prototype.name) {}
                }
            }
            input(ironPlate)
            output(ironGearWheel, 1.perSecond)
            costs {
                costOf(foo, 1e5) // heavily penalize assembling-machine-2
            }
        }
        val solution = problem.solve().solution!!

        val processes = solution.processes.mapKeysNotNull { it as? MachineSetup<*> }
//        val asm2Recipe = problem.recipes.keys.find {
//            it.machine.prototype.name == "assembling-machine-2"
//        }
//        val asm1Recipe = problem.recipes.keys.find {
//            it.machine.prototype.name == "assembling-machine-1"
//        }
        val asm2Usage = processes.entries.find { it.key.machine.prototype.name == "assembling-machine-2" }?.value ?: 0.0
        val asm1Usage = processes.entries.find { it.key.machine.prototype.name == "assembling-machine-1" }?.value ?: 0.0
        asm2Usage shouldBe 0.0
        asm1Usage shouldBe 1.0
    }

    test("all recipes for complex stuff") {
        val problem = problem {
            factory {
                machines {
                    default {
                        moduleConfig()
                        moduleConfig(fill = prod3, beacons = listOf(beacon(fill = speed2) * 4))
                        moduleConfig(fill = speed2)
                        moduleConfig(fill = qual3)
                        includeBuildCosts()
                    }
                    "assembling-machine-3" {}
                    "chemical-plant" {}
                    "oil-refinery" {}
                    "centrifuge" {}
                    "recycler" {}
                    "electromagnetic-plant" {}
                }
                recipes {
                    allCraftingRecipes()
                    default {
                        allQualities()
                    }
                }
            }
            input(ironPlate)
            input(item("steel-plate"))
            input(copperPlate)
            input(item("uranium-ore"))
            input(item("coal"))
            input(fluid("crude-oil"))
            input(fluid("water"))
            input(item("raw-fish"))
            input(item("spoilage"))
            input(item("carbon-fiber"))

            output(item("spidertron").withQuality(epic), (1 / 60).perMinute)

            costs {
                costOf(prod3, 15)
                costOf(qual3, 15)
                limit(item("recycler"), 50)
            }
            surplusCost = 0.0
        }
        val solution = problem.solve(
            options = LpOptions(enableLogging = true)
        )
        solution.status shouldBe LpResultStatus.Optimal
        println(solution.solution!!.processes.display())
    }

    test("integral") {
        val problem = problem {
            factory {
                machines {
                    "assembling-machine-3" {
                        type = VariableType.Integer
                    }
                }
                recipes {
                    (ironGearWheel.prototype.name) {}
                }
            }
            input(ironPlate)
            output(ironGearWheel, 0.01.perSecond)
        }
        val solution = problem.solve().solution!!
        val usage = solution.processes.entries.single().value
        usage shouldBe 1.0
    }

    test("with mining") {
        val problem = problem {
            factory {
                machines {
                    "electric-mining-drill" {}
                    "electric-furnace" {}
                }
                recipes {
                    mining("iron-ore") {}
                    "iron-plate" {}
                }
            }
            output(item("iron-plate"), 1.perSecond)
        }
        val solution = problem.solve()
        solution.status shouldBe LpResultStatus.Optimal
        println(solution.solution!!.processes.display())
    }
    test("moduled mining") {
        val problem = problem {
            factory {
                machines {
                    "electric-mining-drill" {
                        moduleConfig(fill = speed2)
                    }
                }
                recipes {
                    mining("iron-ore") {}
                }
            }
            output(item("iron-ore"), 1.perSecond)
        }
        val solution = problem.solve()
        solution.status shouldBe LpResultStatus.Optimal
        println(solution.solution!!.processes.display())
    }

    test("purely custom") {
        val foo = Ingredient("foo")
        val foo2 = Ingredient("foo2")
        val problem = problem {
            factory {}
            customProcess("foo crafting") {
                ingredientRate -= vectorWithUnits(foo to 1.0)
                ingredientRate += vectorWithUnits(foo2 to 1.0)
            }
            input(foo)
            output(foo2, 1.perSecond)
        }
        val solution = problem.solve()
        solution.status shouldBe LpResultStatus.Optimal
        println(solution.solution!!.processes.display())

    }

    test("semi-continuous") {
        val problem = problem {
            factory {
                machines {
                    "assembling-machine-3" {
                        type = VariableType.SemiContinuous
                        lowerBound = 2.0
                    }
                }
                recipes {
                    "iron-gear-wheel" {}
                }
            }
            input(ironPlate)
            output(ironGearWheel, 0.01.perSecond)
        }
        val solution = problem.solve().solution!!
        val usage = solution.processes.entries.single().value
        usage shouldBe 2.0
        println(solution.processes.display())
    }
    test("using integral cost") {
        val problem = problem {
            factory {
                machines {
                    "assembling-machine-3" {
                        cost = 10.0
                        integralCost()
                        includeBuildCosts()
                    }
                }
                recipes {
                    "iron-gear-wheel" {}
                }
            }
            input(ironPlate, cost = 0.0)
            output(ironGearWheel, 0.01.perSecond)
        }
        val solution = problem.solve().solution!!
        val usage = solution.processes.entries.single().value
        usage shouldBeIn (0.001..0.1)
        val asm3usage = solution.symbolUsage[item("assembling-machine-3")]
        asm3usage shouldBe 1.0
        val cost = solution.objectiveValue
        cost shouldBe 10.0
        println(solution.processes.display())
    }

    context("Multi stage problem") {
        test("sanity check") {
            val problem = problem {
                factory {
                    machines {
                        "assembling-machine-1" {}
                    }
                    recipes {
                        ironGearWheel {}
                    }
                }
                input(ironPlate)
                output(ironGearWheel, 1.perSecond)
            }
            val stage = problem.toStage()
            val multiProblem = MultiStageProductionLp(stages = listOf(stage))
            val result = multiProblem.solve()
            result.status shouldBe LpResultStatus.Optimal
            val stage1Solution = result.solutions!![stage]!!
            stage1Solution.processes.values.single() shouldBe 1.0
        }

    }

}), WithFactorioPrototypes {
    override val prototypes get() = SpaceAge
}
*/
