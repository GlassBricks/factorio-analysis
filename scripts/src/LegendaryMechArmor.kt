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

                moduleConfig()

                for (q in listOf(uncommon, rare, epic, legendary)) {
                    for (module in modules) {
                        moduleConfig(fill = module.withQuality(q))
                        moduleConfig(fill = module.withQuality(q), beacons = listOf(beacon(fill = speedModule)))
                        moduleConfig(fill = module.withQuality(q), beacons = listOf(beacon(fill = speedModule2)))
                    }
                    moduleConfig(fill = qualityModule.withQuality(q))
                    moduleConfig(fill = qualityModule2.withQuality(q))
                }
            }
            bigMiningDrill {}
            electromagneticPlant()
            assemblingMachine3()
            foundry()
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
        }
    }

    val production = vulcanusFactory.problem {
        input(lava, cost = 0.0)
        input(sulfuricAcid, cost = 0.05)

        fun addWithQualities(item: Item, baseCost: Double, rocketCapacity: Double) {
            val shippingCost = 1e6 / rocketCapacity
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
            costOf(electromagneticPlant.item(), 50 + 1.0)
            costOf(beacon.item(), 4 + 1.0)
            costOf(bigMiningDrill.item(), 5 + 2)

//            limit(bigMiningDrill.item(), 100)

            val qualityCostMultiplier = 5.0
            fun addQualityCost(item: Item, baseCost: Double) {
                for ((index, quality) in qualities.withIndex()) {
                    costOf(item.withQuality(quality), baseCost * qualityCostMultiplier.pow(index))
                }
            }
            for (module in listOf(speedModule, productivityModule, qualityModule)) {
                addQualityCost(module, 1.0)
            }
            for (module in listOf(speedModule2, productivityModule2, qualityModule2)) {
                addQualityCost(module, 5.0)
            }
        }

        // 15x17
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
                ingredientRate = vectorWithUnits(prev to -2.0, next to 1.0)
                cost = 0.0
            }
        }

        val fullArmorGrid = Ingredient("full armor")
        val (grid1x1, grid1x2, grid2x2, grid2x4, grid4x4) = gridVars.values.toList()
        customProcess("full armor") {
            ingredientRate += vectorWithUnits(fullArmorGrid to 1.0)
            ingredientRate -= vectorWithUnits(
                grid4x4 to 12.0,
                grid2x4 to 4.0,
                grid1x2 to 8.0,
                grid1x1 to 15.0
            )
        }

        for (item in prototypes.items.values) {
            val placeResult = item.prototype.place_as_equipment_result
            if (placeResult.isNotBlank()) {
                val shape = prototypes.equipment[placeResult]!!.shape
                if (shape.type == EquipmentShapeType.manual) continue
                val size = Size(shape.width.toInt(), shape.height.toInt())
                val grid = gridVars[size] ?: continue
                customProcess("grid $item") {
                    ingredientRate += vectorWithUnits(grid to 1.0)
                    ingredientRate -= vectorWithUnits(item.withQuality(legendary) to 1.0)
                }
            }
        }

        val time = Time(60.0 * 60.0)

        output(
            mechArmor.withQuality(legendary),
            rate = 1.0 / time
        )
        output(
            fullArmorGrid,
            rate = 1.0 / time
        )

        lpOptions = LpOptions(
            solver = OrToolsLp("GLOP"),
            timeLimit = 15.minutes,
            epsilon = 1e-5
        )
    }
    val solution = production.solve()
    println(solution.status)
    val display = solution.recipeSolution?.display()
    println(display)
    display?.let {
        File("output/legendary-mech-armor.txt").also { it.parentFile.mkdirs() }.writeText(it)
    }
}
