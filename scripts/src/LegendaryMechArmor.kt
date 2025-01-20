import glassbricks.factorio.prototypes.EquipmentShapeType
import glassbricks.factorio.recipes.*
import glassbricks.recipeanalysis.*
import java.io.File
import kotlin.math.pow
import kotlin.time.Duration.Companion.minutes

fun main() {
    val vulcanusFactory = SpaceAge.factory {
        val beacons = listOf(
            speedModule,
            speedModule2
        ).flatMap { module ->
            qualities.flatMap { q ->
                listOf(
                    beacon(fill = module.withQuality(q), sharing = 8.0),
                    beacon(fill = module.withQuality(q), sharing = 6.0) * 2,
                )
            }
        }

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

                for (q in qualities) {
                    for (module in modules) {
                        moduleConfig(fill = module.withQuality(q))
                        for (beacon in beacons) {
                            moduleConfig(fill = module.withQuality(q), beacons = listOf(beacon))
                        }
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

        fun addWithQualities(item: Item, baseCost: Double) {
            input(item, cost = baseCost)
            input(item.withQuality(uncommon), cost = baseCost * 5)
            input(item.withQuality(rare), cost = baseCost * 5 * 5)
            input(item.withQuality(epic), cost = baseCost * 5 * 5 * 5)
            input(item.withQuality(legendary), cost = baseCost * 5 * 5 * 5 * 5)
        }
        addWithQualities(holmiumPlate, 100.0)
        addWithQualities(supercapacitor, 350.0)

        costs {
            costOf(assemblingMachine3.item(), 3)
            costOf(foundry.item(), 5)
            costOf(recycler.item(), 10)
            costOf(electromagneticPlant.item(), 50)
            costOf(beacon.item(), 4)

            limit(bigMiningDrill.item(), 100)

            val qualityCostMultiplier = 3.5
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
                ingredientRate = vector(prev to -2.0, next to 1.0)
                cost = 0.0
            }
        }

        val fullArmorGrid = Ingredient("full armor")
        val (grid1x1, grid1x2, grid2x2, grid2x4, grid4x4) = gridVars.values.toList()
        customProcess("full armor") {
            ingredientRate += vector(fullArmorGrid to 1.0)
            ingredientRate -= vector(
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
                    ingredientRate += vector(grid to 1.0)
                    ingredientRate -= vector(item.withQuality(legendary) to 1.0)
                }
            }
        }

        val time = Time(60.0 * 30.0)

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
