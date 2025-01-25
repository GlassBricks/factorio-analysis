import glassbricks.factorio.prototypes.EquipmentShapeType
import glassbricks.factorio.recipes.*
import glassbricks.factorio.recipes.problem.factory
import glassbricks.factorio.recipes.problem.problem
import glassbricks.recipeanalysis.*
import java.io.File
import kotlin.math.pow
import kotlin.time.Duration.Companion.minutes

fun main() {
    val vulcanusFactory = SpaceAge.factory {
        /*  val beacons = listOf(
              speedModule,
              speedModule2
          ).flatMap { module ->
              qualities.flatMap { q ->
                  listOf(
                      beacon(fill = module.withQuality(q), sharing = 8.0),
                      beacon(fill = module.withQuality(q), sharing = 6.0) * 2,
                  )
              }
          }*/

        val modules = listOf(
            speedModule,
            speedModule2,
            productivityModule,
            productivityModule2,
        )
        machines {
            default {
                includeBuildCosts()
                cost = 0.0

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
                }
            }
            bigMiningDrill {}
            electromagneticPlant {
//                integralCost()
//                semiContinuousCost(1.0)
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
            efficiencyModule {
//                setQualities(rare)
            }
            speedModule {
//                setQualities(rare)
            }
        }
    }

    val production = vulcanusFactory.problem {
        input(lava, cost = 0.0)
        input(sulfuricAcid, cost = 0.05)

        fun addWithQualities(item: Item, baseCost: Double, rocketCapacity: Double) {
            val shippingCost = 1e7 / rocketCapacity
            val qualityCost = 8.0
            for ((index, quality) in qualities.withIndex()) {
                input(item.withQuality(quality), cost = baseCost * qualityCost.pow(index) + shippingCost)
            }
        }
        addWithQualities(holmiumOre, 150.0, 500.0)
        addWithQualities(holmiumPlate, 150 / 2.5, 1000.0)
//        addWithQualities(supercapacitor, 50.0, 500.0)

        costs {
            costOf(assemblingMachine3.item(), 3 + 1.0)
            costOf(foundry.item(), 5 + 2.0)
            costOf(recycler.item(), 10 + 1.0)
            costOf(electromagneticPlant.item(), 100)
            costOf(beacon.item(), 4 + 1.0)
            costOf(bigMiningDrill.item(), 5 + 2)

            limit(bigMiningDrill.item(), 100)

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
        }

        val targetTime = Time(60.0 * 60.0)

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

        lpSolver = OrToolsLp("SCIP")
        lpOptions = LpOptions(
            timeLimit = 10.minutes,
            numThreads = Runtime.getRuntime().availableProcessors() - 2,
//            enableLogging = true,
            epsilon = 1e-5
        )
    }
    val result = production.solve()
    println("Status: ${result.status}")
    println("Best bound: ${result.lpResult.bestBound}")
    result.solution?.let {
        println("Objective: ${result.lpSolution!!.objectiveValue}")
        val display = it.display()
        println(display)
        File("output/legendary-mech-armor.txt").also { it.parentFile.mkdirs() }.writeText(display)
    }
}
