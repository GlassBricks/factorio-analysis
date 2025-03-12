package glassbricks.factorio.recipes.problem

import glassbricks.factorio.prototypes.EntityPrototype
import glassbricks.factorio.recipes.*
import glassbricks.recipeanalysis.*
import glassbricks.recipeanalysis.lp.LpOptions
import glassbricks.recipeanalysis.lp.LpResultStatus
import glassbricks.recipeanalysis.lp.OrToolsLpSolver
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
                gc {}
                ironGearWheel {}
                // something irrelevant
                "iron-chest" {}
            }
        }
    }
    test("solve maximize") {
        val problem = gcFactory.problem {
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
        val problem = gcFactory.problem {
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
        val problem = basicGamblingFactory.problem {
            input(ironPlate)
            output(ironPlate.withQuality(legendary), 6.perMinute)
        }
        val solution = problem.solve()
        solution.status shouldBe LpResultStatus.Optimal
        println(solution.solution!!.processes.display())
    }

    test("can cast pipe") {
        val factory = factory {
            machines {
                "foundry" {}
            }
            recipes {
                "casting-pipe" {}
                "casting-iron" {}
                "casting-pipe-to-ground" {}
            }
        }
        val problem = factory.problem {
            input(fluid("molten-iron"))
            output(item("pipe-to-ground"), 1.perSecond)
        }
        val solution = problem.solve()
        solution.status shouldBe LpResultStatus.Optimal
        println(solution.solution!!.processes.display())
    }
    test("can have excess") {
        val factory = factory {
            machines {
                "recycler" {}
            }
            recipes {
                "pipe-to-ground-recycling" {}
            }
        }
        val problem = factory.problem {
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

                allOfCategory("recycling")
            }
        }
        val problem = gamblingFactory.problem {
            val moltenIron = fluid("molten-iron")
            input(moltenIron)
            output(ironPlate.withQuality(rare), 6.perMinute)
        }
        val solution = problem.solve()
        solution.status shouldBe LpResultStatus.Optimal
        println(solution.solution!!.processes.display())
    }

    test("constrain machines") {
        val factory = factory {
            includeBuildCosts()
            machines {
                "assembling-machine-1" {}
            }
            recipes {
                ironGearWheel {
                    upperBound = 1.0
                }
            }
        }
        val problem = factory.problem {
            input(ironPlate)
//            maximize(ironGearWheel)
            output(ironGearWheel, 1.perSecond)
            costs {
//                limit(item("assembling-machine-1"), 1.0)
            }
        }
        val solution = problem.solve().solution!!
        println(solution.processes.display())

        val inputRate = Rate(solution.inputs[ironPlate])
        inputRate shouldBe 2.perSecond
        val outputRate = Rate(solution.outputs[ironGearWheel])
        outputRate shouldBe 1.perSecond

        val recipeUsage = solution.processes.values.single()
        recipeUsage shouldBe 1.0
    }

    test("constrain modules") {
        val factory = factory {
            includeBuildCosts()
            machines {
                "assembling-machine-2" {
                    moduleConfig()
                    moduleConfig(fill = prod3)
                }
            }
            recipes {
                (ironGearWheel.prototype.name) {}
            }
        }
        val problem = factory.problem {
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

        val factory = factory {
            includeBuildCosts()
            machines {
                default {
                }
                "assembling-machine-2" {
                    additionalCosts += vectorOf(foo to 2.0)
                }
                "assembling-machine-1" {
                    additionalCosts += vectorOf(foo to 1.0)
                }
            }
            recipes {
                (ironGearWheel.prototype.name) {}
            }
        }
        val problem = factory.problem {
            input(ironPlate)
            output(ironGearWheel, 1.perSecond)
            costs {
                costOf(foo, 1e5) // heavily penalize assembling-machine-2
            }
        }
        val solution = problem.solve().solution!!

        val processes = solution.processes.mapKeysNotNull { it as? MachineProcess<*> }
        val asm2Usage =
            processes.find { (it.key.machine.prototype as EntityPrototype).name == "assembling-machine-2" }?.doubleValue
                ?: 0.0
        val asm1Usage =
            processes.find { (it.key.machine.prototype as EntityPrototype).name == "assembling-machine-1" }?.doubleValue
                ?: 0.0
        asm2Usage shouldBe 0.0
        asm1Usage shouldBe 1.0
    }

    test("all recipes for complex stuff") {
        val factory = factory {
            includeBuildCosts()
            machines {
                default {
                    moduleConfig()
                    moduleConfig(fill = prod3, beacons = listOf(beacon(fill = speed2) * 4))
                    moduleConfig(fill = speed2)
                    moduleConfig(fill = qual3)
                }
                "assembling-machine-3" {}
                "chemical-plant" {}
                "oil-refinery" {}
                "centrifuge" {}
                "recycler" {}
                "electromagnetic-plant" {}
            }
            recipes {
                default { allQualities() }
                allCraftingRecipes()
            }
        }
        val problem = factory.problem {
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
        val factory = factory {
            machines {
                "assembling-machine-3" {
                    integral()
                }
            }
            recipes {
                (ironGearWheel.prototype.name) {}
            }
        }
        val problem = factory.problem {
            input(ironPlate)
            output(ironGearWheel, 0.01.perSecond)
        }
        val solution = problem.solve(solver = OrToolsLpSolver("SCIP")).solution!!
        val usage = solution.processes.single().doubleValue
        usage shouldBe 1.0
    }

    test("with mining") {
        val factory = factory {
            machines {
                "electric-mining-drill" {}
                "electric-furnace" {}
            }
            recipes {
                (resource("iron-ore")) {}
                "iron-plate" {}
            }
        }
        val problem = factory.problem {
            output(item("iron-plate"), 1.perSecond)
        }
        val solution = problem.solve()
        solution.status shouldBe LpResultStatus.Optimal
        println(solution.solution!!.processes.display())
    }
    test("moduled mining") {
        val factory = factory {
            machines {
                "electric-mining-drill" {
                    moduleConfig(fill = speed2)
                }
            }
            recipes {
                (resource("iron-ore")) {}
            }
        }
        val problem = factory.problem {
            output(item("iron-ore"), 1.perSecond)
        }
        val solution = problem.solve()
        solution.status shouldBe LpResultStatus.Optimal
        println(solution.solution!!.processes.display())
    }

    test("purely custom") {
        val foo = Ingredient("foo")
        val foo2 = Ingredient("foo2")
        val problem = factory {}.problem {
            customProcess("foo crafting") {
                ingredientRate -= vectorOfWithUnits(foo to 1.0)
                ingredientRate += vectorOfWithUnits(foo2 to 1.0)
            }
            input(foo)
            output(foo2, 1.perSecond)
        }
        val solution = problem.solve()
        solution.status shouldBe LpResultStatus.Optimal
        println(solution.solution!!.processes.display())

    }

    xtest("semi-continuous") {
        val factory = factory {
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
        val problem = factory.problem {
            input(ironPlate)
            output(ironGearWheel, 0.01.perSecond)
        }
        val solution = problem.solve(solver = OrToolsLpSolver("SCIP")).solution!!
        val usage = solution.processes.single().doubleValue
        usage shouldBe 2.0
        println(solution.processes.display())
    }
    test("using integral cost") {
        val factory = factory {
            includeBuildCosts()
            machines {
                "assembling-machine-3" {
                    cost = 10.0
                    integralCost()
                }
            }
            recipes {
                "iron-gear-wheel" {}
            }
        }
        val problem = factory.problem {
            input(ironPlate, cost = 0.0)
            output(ironGearWheel, 0.01.perSecond)
        }
        val solution = problem.solve(solver = OrToolsLpSolver("SCIP")).solution!!
        val usage = solution.processes.single().doubleValue
        usage shouldBeIn 0.001..0.1
        val asm3usage = solution.symbolUsage[item("assembling-machine-3")]
        asm3usage shouldBe 1.0
        val cost = solution.objectiveValue
        cost shouldBe 10.0
        println(solution.processes.display())
    }

    context("Multi stage problem") {
        val factory = factory {
            machines {
                "assembling-machine-1" {}
            }
            recipes {
                ironGearWheel {}
            }
        }
        test("sanity check") {
            val problem = factory.problem {
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
}), FactorioPrototypesScope {
    override val prototypes get() = SpaceAge
}
