import glassbricks.factorio.prototypes.EquipmentShapeType
import glassbricks.factorio.prototypes.RecipeID
import glassbricks.factorio.recipes.ResearchConfig
import glassbricks.factorio.recipes.SpaceAge
import glassbricks.factorio.recipes.invoke
import glassbricks.factorio.recipes.problem.ProblemBuilder
import glassbricks.factorio.recipes.problem.factory
import glassbricks.factorio.recipes.problem.stage
import glassbricks.recipeanalysis.*
import glassbricks.recipeanalysis.lp.LpOptions
import glassbricks.recipeanalysis.lp.OrToolsLp
import glassbricks.recipeanalysis.recipelp.MultiStageProductionLp
import glassbricks.recipeanalysis.recipelp.runningFor
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

fun main(): Unit = with(SpaceAge) {
    val vulcanusFactory = factory {
        machines {
            default {
                includeBuildCosts()
                moduleConfig()
                for (module in module12sAllQualities) {
                    moduleConfig(fill = module)
                    if (module.effects.quality <= 0) {
                        moduleConfig(
                            fill = module,
                            beacons = listOf(beacon(fill = speedModule2, sharing = 6.0))
                        )
                    }
                }
            }
            bigMiningDrill {}
            electromagneticPlant {}
            assemblingMachine3 {}
            foundry {}
            recycler()
            chemicalPlant()
            oilRefinery()
            electricFurnace()
        }
        researchConfig = ResearchConfig(
            miningProductivity = 0.2,
            recipeProductivity = mapOf(RecipeID(castingLowDensityStructure.prototype.name) to 0.2)
        )
        recipes {
            default { allQualities() }
            allCraftingRecipes()
            calciteMining()
            coalMining {
                cost = 20.0
            }
            tungstenOreMining()
            remove(electromagneticPlant.item())
            remove(lightningCollector)
            remove(lightningRod)
            // manually remove weird recipes that it tries to do 0.002 of
            // which might be a tiny bit more optimal, but too complicated to be worth it
            remove(splitter)
            remove(transportBelt)
//            remove(bigElectricPole)
//            remove(steelChest)
        }
    }

    fun ProblemBuilder.CostsScope.machineCosts() {
        costOf(assemblingMachine3, 3 + 1.0)
        costOf(foundry, 5 + 2.0)
        costOf(beacon, 4 + 1.0)
        costOf(bigMiningDrill, 5 + 2)
        costOf(recycler, 20)
        costOf(electromagneticPlant, 100)
    }

    val stage1 = vulcanusFactory.stage("modules") {
        input(lava, cost = 0.0)
        input(sulfuricAcid, cost = 0.0005)
//        input(holmiumOre, limit = 500.0 / 30.minutes)
//        input(holmiumPlate.withQuality(epic), limit = 300.0 / 30.minutes)
        for (q in qualities) {
            input(superconductor.withQuality(q), cost = 0.0)
        }

        for (module in module12sAllQualities) {
            optionalOutput(module)
        }
        costs {
            machineCosts()
            for (module in module1s) {
                costOf(module, 1.0)
            }
            for (module in module2s) {
                costOf(module, 5.0)
            }
            forbidUnspecifiedModules()
        }
    }

    val stage1Production = stage1.runningFor(30.minutes)

    val targetTime = 0.5.hours.asTime()
    val finalProduction = vulcanusFactory.stage("final production") {
        input(lava, cost = 0.0)
        input(sulfuricAcid, cost = 0.0005)
        limit(holmiumOre, 500.0 / targetTime)
        limit(holmiumPlate.withQuality(epic), 1000.0 / targetTime)
        input(holmiumPlate.withQuality(legendary), limit = 100.0 / targetTime)

        costs {
            machineCosts()
            for (module in module12sAllQualities) {
                module producedBy stage1Production
            }
        }


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

    val problem = MultiStageProductionLp(
        stages = listOf(stage1, finalProduction)
    )

    val result = problem.solve(
        OrToolsLp(), LpOptions(
            timeLimit = 10.minutes,
            numThreads = Runtime.getRuntime().availableProcessors() - 2,
            enableLogging = true,
            epsilon = 1e-5
        )
    )
    println("Status: ${result.status}")
    val solution = result.solutions ?: return
    printAndExportStagedSolution("output/legendary-armor-staged", solution)
}
