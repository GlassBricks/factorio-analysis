package glassbricks.factorio.recipes

import glassbricks.recipeanalysis.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class ProblemTest : FunSpec({
    this as ProblemTest
    val gc = item("electronic-circuit")
    val ironPlate = item("iron-plate")
    val copperPlate = item("copper-plate")
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
            "iron-gear-wheel" {}
            // something irrelevant
            "iron-chest" {}
        }
    }
    test("solve maximize") {
        val problem = problem {
            factory = gcFactory
            limit("copper-plate", 3.perSecond)
            limit("iron-plate", 3.perSecond)
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
            input("copper-plate")
            input("iron-plate")
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
                default { addAllQualities() }
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
        println(solution.recipeSolution!!.recipes.display())
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
        println(solution.recipeSolution!!.recipes.display())
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
            output(item("iron-plate"), 1.perSecond)
        }
        val solution = problem.solve()
        solution.status shouldBe LpResultStatus.Optimal
        println(solution.recipeSolution!!.recipes.display())
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
                    addAllQualities()
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
        println(solution.recipeSolution!!.recipes.display())
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
                    "iron-gear-wheel" {}
                }
            }
            input(ironPlate)
            maximize("iron-gear-wheel")
            costs {
                limit("assembling-machine-1", 1.0)
            }
        }
        val solution = problem.solve()
        solution.status shouldBe LpResultStatus.Optimal

        println(solution.lpSolution?.objective)

        val inputRate = solution.inputRate(ironPlate)!!
        inputRate shouldBe 2.perSecond
        val outputRate = solution.outputRate(item("iron-gear-wheel"))!!
        outputRate shouldBe 1.perSecond

        val recipe = problem.recipes.keys.single()

        val recipeUsage = solution.recipesUsed(recipe)
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
                    "iron-gear-wheel" {}
                }
            }
            input(ironPlate)
            maximize("iron-gear-wheel")
            costs {
                limit(prod3, 2.0)
                limit("assembling-machine-2", 1.0)
            }
        }
        val solution = problem.solve()
        solution.status shouldBe LpResultStatus.Optimal
        println(solution.recipeSolution!!.recipes.display())
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
                        additionalCosts += vector(foo to 2.0)
                    }
                    "assembling-machine-1" {
                        additionalCosts += vector(foo to 1.0)
                    }
                }
                recipes {
                    "iron-gear-wheel" {}
                }
            }
            input(ironPlate)
            output("iron-gear-wheel", 1.perSecond)
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
        val asm2Usage = solution.recipesUsed(asm2Recipe!!)
        val asm1Usage = solution.recipesUsed(asm1Recipe!!)
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
                    addAllRecipes()
                    default {
                        addAllQualities()
                    }
                }
            }
            input("iron-plate")
            input("steel-plate")
            input("copper-plate")
            input("uranium-ore")
            input("coal")
            input("crude-oil")
            input("water")
            input("raw-fish")
            input("spoilage")
            input("carbon-fiber")

            output(item("spidertron").withQuality(legendary), 1.perMinute)

            costs {
                costOf(prod3, 15)
                costOf(qual3, 15)
                limit("recycler", 50)
            }
            surplusCost = 0.0
            lpOptions = LpOptions(timeLimit = 15.seconds, solver = OrToolsLp("CLP"))
        }
        val solution = problem.solve()
        solution.status shouldBe LpResultStatus.Optimal
        println(solution.recipeSolution!!.recipes.display())
    }

}), WithFactorioPrototypes {
    override val prototypes get() = SpaceAge
}
