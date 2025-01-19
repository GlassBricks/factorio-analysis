package glassbricks.factorio.recipes

import glassbricks.recipeanalysis.LpResultStatus
import glassbricks.recipeanalysis.perMinute
import glassbricks.recipeanalysis.perSecond
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ProblemTest : FunSpec({
    with(SpaceAge) {
        val gc = item("electronic-circuit")
        val ironPlate = item("iron-plate")
        val copperPlate = item("copper-plate")
        val speed1 = module("speed-module")
        val speed2 = module("speed-module-2")
        val prod3 = module("productivity-module-2")
        val qual3 = module("quality-module-3")

        val (normal, uncommon, rare, epic, legendary) = qualities

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
            println(solution.lpSolution!!.recipes.display())
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
            println(solution.lpSolution!!.recipes.display())
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
            println(solution.lpSolution!!.recipes.display())
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
            println(solution.lpSolution!!.recipes.display())
        }
    }
})
