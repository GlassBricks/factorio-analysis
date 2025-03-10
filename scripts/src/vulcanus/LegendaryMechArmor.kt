package scripts.vulcanus

import glassbricks.factorio.prototypes.EquipmentShapeType
import glassbricks.factorio.prototypes.RecipeID
import glassbricks.factorio.recipes.ResearchConfig
import glassbricks.factorio.recipes.SpaceAge
import glassbricks.factorio.recipes.problem.ProblemBuilder
import glassbricks.factorio.recipes.problem.factory
import glassbricks.factorio.recipes.problem.problem
import glassbricks.recipeanalysis.*
import glassbricks.recipeanalysis.lp.LpOptions
import scripts.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun main(): Unit = with(SpaceAge) {
    val vulcanusFactory = factory {
        vulcanusMachines()
        researchConfig = ResearchConfig(
            miningProductivity = 0.2,
            recipeProductivity = mapOf(
                RecipeID(castingLowDensityStructure.prototype.name) to 0.1,
            ),
        )
        recipes {
            default {
                allQualities()
                cost = 1.0
            }
            allCraftingRecipes()
            calciteMining()
            coalMining {
                cost = 60.0
            }
            tungstenOreMining()
            remove(electromagneticPlant)
            remove(lightningCollector)
            remove(lightningRod)
            remove(supercapacitorRecycling)
            remove(superconductorRecycling)
        }
    }

    val targetTime = 40.minutes

    val production = vulcanusFactory.problem {
        input(lava, cost = 0.0)
        input(sulfuricAcid, cost = 0.0005)
        input(electronicCircuit, limit = 5e6 / targetTime, cost = 0.0)
        limit(holmiumPlate.withQuality(epic), 1000.0 / targetTime)
        limit(supercapacitor.withQuality(epic), 250.0 / targetTime)
        limit(superconductor.withQuality(epic), 250.0 / targetTime)
        limit(holmiumPlate.withQuality(legendary), 40.0 / targetTime)
        limit(supercapacitor.withQuality(legendary), 10.0 / targetTime)
        limit(superconductor.withQuality(legendary), 10.0 / targetTime)

        oneFullLegendaryMechArmor(targetTime)

        costs {
            vulcanusMachineCosts1()
            vulcanusModuleCosts1()
            limit(qualityModule3, 0)
            limit(qualityModule3.withQuality(uncommon), 0.0)
            limit(qualityModule3.withQuality(rare), 0.0)
            forbidAllUnspecified()
        }
    }

    val result = production.solve(
        options = LpOptions(
            timeLimit = 10.minutes,
            numThreads = Runtime.getRuntime().availableProcessors() - 2,
            enableLogging = true,
            epsilon = 1e-5
        )
    )
    println("Status: ${result.status}")
    println("Best bound: ${result.lpResult.bestBound}")
    result.solution?.let {
        printAndExportSolution("output/legendary-mech-armor", it)
    }

}

fun ProblemBuilder.oneFullLegendaryMechArmor(targetTime: Duration) = with(prototypes) {
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
            ingredientRate = vectorOf<Ingredient>(prev to -2.0, next to 1.0) / targetTime
        }
    }

    val (grid1x1, grid1x2, _, grid2x4, grid4x4) = gridVars.values.toList()
    for (item in prototypes.items.values) {
        val placeResult = item.prototype.place_as_equipment_result
        if (placeResult.isBlank()) continue
        val shape = prototypes.equipment[placeResult]!!.shape
        if (shape.type == EquipmentShapeType.manual) continue
        val size = Size(shape.width.toInt(), shape.height.toInt())
        val grid = gridVars[size] ?: continue
        customProcess("grid $item") {
            ingredientRate += vectorOf(grid to 1.0) / targetTime
            ingredientRate -= vectorOf(item.withQuality(legendary) to 1.0) / targetTime
        }
    }
    output(mechArmor.withQuality(legendary), rate = 1.0 / targetTime)
    output(grid4x4, rate = 12.0 / targetTime)
    output(grid2x4, rate = 4.0 / targetTime)
    output(grid1x2, rate = 8.0 / targetTime)
    output(grid1x1, rate = 15.0 / targetTime)
}
