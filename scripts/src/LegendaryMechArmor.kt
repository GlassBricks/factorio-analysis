import glassbricks.factorio.prototypes.EquipmentShapeType
import glassbricks.factorio.recipes.*
import glassbricks.factorio.recipes.problem.MachineConfig
import glassbricks.factorio.recipes.problem.factory
import glassbricks.factorio.recipes.problem.problem
import glassbricks.recipeanalysis.*
import glassbricks.recipeanalysis.lp.LpOptions
import glassbricks.recipeanalysis.lp.OrToolsLp
import glassbricks.recipeanalysis.recipelp.textDisplay
import java.io.File
import kotlin.math.pow
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

fun main() {
    val vulcanusFactory = SpaceAge.factory {
        val modules = listOf(
            speedModule,
            speedModule2,
            productivityModule,
            productivityModule2,
        )
        machines {
            default {
                includeBuildCosts()

                moduleConfig()

                for (q in prototypes.qualities) {
                    for (module in modules) {
                        moduleConfig(fill = module.withQuality(q))
                        moduleConfig(
                            fill = module.withQuality(q),
                            beacons = listOf(beacon(fill = speedModule2, sharing = 6.0))
                        )
                    }
                    moduleConfig(fill = qualityModule.withQuality(q))
                    moduleConfig(fill = qualityModule2.withQuality(q))
//                    moduleConfig(fill = qualityModule3.withQuality(q))
                }
            }
            bigMiningDrill {}
            electromagneticPlant {
//                integralCost()
                semiContinuousCost(1.0)
            }
            assemblingMachine3 {
//                integralCost()
            }
            foundry {
//                integral()
            }
            recycler() // not integral as we can easily share recyclers
            chemicalPlant()
            oilRefinery()
            electricFurnace()
        }
        researchConfig = ResearchConfig(
            miningProductivity = 0.2
        )
        recipes {
            default {
                allQualities()
            }
            allRecipes()
            calciteMining()
            coalMining()
            var noQuality: (MachineConfig) -> Boolean = {
                it.machine.modulesUsed.none {
                    it.prototype == qualityModule.prototype || it.prototype == qualityModule2.prototype
                }
            }
            electricEngineUnit {
                setQualities(rare)
                filters += noQuality
            }
            allRecycling {
                val process = process as Recipe
                if (process != powerArmorMk2Recycling &&
                    process.outputs.any {
                        (it.key as? Item)?.prototype == electricEngineUnit.prototype
                    }
                ) {
                    filters += { false }
                }
            }
            remove(electromagneticPlant.item())
            remove(lightningCollector)
            remove(lightningRod)
        }
    }

    val production = vulcanusFactory.problem {
        input(lava, cost = 0.0)
        input(sulfuricAcid, cost = 0.0005)
        input(holmiumOre, cost = 200.0 + 1e7 / 500)

        costs {
            costOf(assemblingMachine3.item(), 3 + 1.0)
            costOf(foundry.item(), 5 + 2.0)
            costOf(recycler.item(), 10 + 1.0)
            costOf(electromagneticPlant.item(), 100)
            costOf(beacon.item(), 4 + 1.0)
            costOf(bigMiningDrill.item(), 5 + 2)

            val qualityCostMultiplier = 5.0
            fun addQualityCost(item: Item, baseCost: Double) {
                for ((index, quality) in qualities.withIndex()) {
                    var value = baseCost * qualityCostMultiplier.pow(index)
                    costOf(item.withQuality(quality), value)
                }
            }
            for (module in listOf(speedModule, productivityModule, qualityModule)) {
                addQualityCost(module, 1.0)
            }
            for (module in listOf(speedModule2, productivityModule2, qualityModule2)) {
                addQualityCost(module, 5.0)
            }
//            for (module in listOf(speedModule3, productivityModule3, qualityModule3)) {
//                addQualityCost(module, 25.0)
//            }
        }

        val targetTime = 1.hours.asTime()

        data class Size(val x: Int, val y: Int)

        val sizes = listOf(
            Size(1, 1),
            Size(1, 2),
            Size(2, 2),
            Size(2, 4),
            Size(4, 4)
        )
        val gridVars = sizes.associateWith { size ->
            Ingredient("grid ${size.x}x${size.y}")
        }
        for ((prev, next) in gridVars.values.zipWithNext()) {
            customProcess("doubling $prev") {
                ingredientRate = vector<Ingredient>(prev to -2.0, next to 1.0) / targetTime
            }
        }

        val fullArmorGrid = Ingredient("full armor")
        val (grid1x1, grid1x2, _, grid2x4, grid4x4) = gridVars.values.toList()
        customProcess("full armor") {
            ingredientRate += vector<Ingredient>(fullArmorGrid to 1.0) / targetTime
            ingredientRate -= vector<Ingredient>(
                grid4x4 to 12.0,
                grid2x4 to 4.0,
                grid1x2 to 8.0,
                grid1x1 to 15.0
            ) / targetTime
        }

        for (item in prototypes.items.values) {
            val placeResult = item.prototype.place_as_equipment_result
            if (placeResult.isNotBlank()) {
                val shape = prototypes.equipment[placeResult]!!.shape
                if (shape.type == EquipmentShapeType.manual) continue
                val size = Size(shape.width.toInt(), shape.height.toInt())
                val grid = gridVars[size] ?: continue
                customProcess("grid $item") {
                    ingredientRate += vector<Ingredient>(grid to 1.0) / targetTime
                    ingredientRate -= vector<Ingredient>(item.withQuality(legendary) to 1.0) / targetTime
                }
            }
        }


        output(
            mechArmor.withQuality(legendary),
            rate = 1.0 / targetTime
        )
        output(
            fullArmorGrid,
            rate = 1.0 / targetTime
        )

    }

    val result = production.solve(
        OrToolsLp("SCIP"), LpOptions(
            timeLimit = 10.minutes,
            numThreads = Runtime.getRuntime().availableProcessors() - 2,
            enableLogging = true,
            //            hintFromRoundingUpSemiContinuousVars = true,
            epsilon = 1e-5
        )
    )
    println("Status: ${result.status}")
    println("Best bound: ${result.lpResult.bestBound}")
    result.solution?.let {
        println("Objective: ${result.lpSolution!!.objectiveValue}")
        val display = it.textDisplay(RecipesFirst)
        println(display)
        File("output/legendary-mech-armor.txt").also { it.parentFile.mkdirs() }.writeText(display)

        it.toBlueprint().exportTo(File("output/legendary-mech-armor-bp.txt"))
    }
}
