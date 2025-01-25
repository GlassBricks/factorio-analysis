package glassbricks.factorio.recipes

import glassbricks.factorio.recipes.problem.factory
import glassbricks.factorio.recipes.problem.problem
import glassbricks.recipeanalysis.*
import io.kotest.core.spec.style.FunSpec
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

    val (normal, uncommon, rare, epic, legendary) = prototypes.qualities

    val gcFactory = factory {
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
    test("solve maximize") {
        val problem = problem {
            factory = gcFactory
            limit(copperPlate, 3.perSecond)
            limit(ironPlate, 3.perSecond)
            maximize(gc)
        }
        val solution = problem.solve()
        solution.outputRate(gc) shouldBe 2.perSecond
        solution.inputRate(ironPlate) shouldBe 2.perSecond
        solution.inputRate(copperPlate) shouldBe 3.perSecond
    }
    test("solve limit") {
        val problem = problem {
            factory = gcFactory
            input(copperPlate)
            input(ironPlate)
            output(gc, 2.perSecond)
        }
        val solution = problem.solve()
        solution.outputRate(gc) shouldBe 2.perSecond
        solution.inputRate(ironPlate) shouldBe 2.perSecond
        solution.inputRate(copperPlate) shouldBe 3.0.perSecond
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
        println(solution.solution!!.recipeUsage.display())
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
        println(solution.solution!!.recipeUsage.display())
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
        println(solution.solution!!.recipeUsage.display())
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
            output(ironPlate.withQuality(legendary), 6.perMinute)
        }
        val solution = problem.solve()
        solution.status shouldBe LpResultStatus.Optimal
        println(solution.solution!!.recipeUsage.display())
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
        val solution = problem.solve()
        solution.status shouldBe LpResultStatus.Optimal

        println(solution.lpSolution?.objectiveValue)

        val inputRate = solution.inputRate(ironPlate)!!
        inputRate shouldBe 2.perSecond
        val outputRate = solution.outputRate(ironGearWheel)!!
        outputRate shouldBe 1.perSecond

        val recipe = problem.recipes.keys.single()

        val recipeUsage = solution.amountUsed(recipe)
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
        println(solution.solution!!.recipeUsage.display())
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
        val solution = problem.solve()
        solution.status shouldBe LpResultStatus.Optimal

        val asm2Recipe = problem.recipes.keys.find {
            it.machine.prototype.name == "assembling-machine-2"
        }
        val asm1Recipe = problem.recipes.keys.find {
            it.machine.prototype.name == "assembling-machine-1"
        }
        val asm2Usage = solution.amountUsed(asm2Recipe!!)
        val asm1Usage = solution.amountUsed(asm1Recipe!!)
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
                    allRecipes()
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
            lpOptions = LpOptions(enableLogging = true)
        }
        val solution = problem.solve()
        solution.status shouldBe LpResultStatus.Optimal
        println(solution.solution!!.recipeUsage.display())
    }

    test("integral") {
        val problem = problem {
            factory {
                machines {
                    "assembling-machine-3" {
                        integral()
                    }
                }
                recipes {
                    (ironGearWheel.prototype.name) {}
                }
            }
            input(ironPlate)
            output(ironGearWheel, 0.01.perSecond)
        }
        println(problem.factory.allProcesses.first())
        val solution = problem.solve()
        solution.status shouldBe LpResultStatus.Optimal
        val usage = solution.amountUsed(problem.recipes.keys.single())
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
        println(solution.solution!!.recipeUsage.display())
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
        println(solution.solution!!.recipeUsage.display())
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
        println(solution.solution!!.recipeUsage.display())

    }

    test("semi-continuous") {
        val problem = problem {
            factory {
                machines {
                    "assembling-machine-3" {
                        semiContinuous(lowerBound = 2.0)
                    }
                }
                recipes {
                    "iron-gear-wheel" {}
                }
            }
            input(ironPlate)
            output(ironGearWheel, 0.01.perSecond)
        }
        val solution = problem.solve()
        solution.status shouldBe LpResultStatus.Optimal
        val usage = solution.amountUsed(problem.recipes.keys.single())
        usage shouldBe 2.0
        println(solution.solution!!.recipeUsage.display())
    }
}), WithFactorioPrototypes {
    override val prototypes get() = SpaceAge
}
