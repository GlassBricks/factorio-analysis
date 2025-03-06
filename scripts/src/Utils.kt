@file:Suppress("unused")

package scripts

import glassbricks.factorio.recipes.*
import glassbricks.factorio.recipes.export.*
import glassbricks.factorio.recipes.problem.ProblemBuilder
import glassbricks.recipeanalysis.recipelp.ProductionStage
import glassbricks.recipeanalysis.recipelp.RecipeSolution
import glassbricks.recipeanalysis.recipelp.textDisplay
import glassbricks.recipeanalysis.recipelp.toThroughputGraph
import glassbricks.recipeanalysis.writeDotGraph
import java.io.File

fun printAndExportStagedSolution(
    pathName: String,
    solution: Map<ProductionStage, RecipeSolution>,
    formatter: FactorioRecipesFormatter,
) {
    val dotFilePath = File(pathName)
    dotFilePath.mkdirs()
    for ((stage, solution) in solution) {
        val display = solution.textDisplay(RecipesFirst)
        dotFilePath.resolve("${stage.name}.txt").writeText(display)

        println("Stage: $stage")
        println("-".repeat(100))
        println(display)
        println()

        val dotFile = dotFilePath.resolve("${stage.name}.dot")
        val graph = solution.toThroughputGraph {
            mergeItemsByQuality()
            mergeRecipesByQuality()
        }.toFancyDotGraph(
            formatter
        )
        dotFile.writeDotGraph(graph)

    }
}

fun printAndExportSolution(
    pathPrefix: String, solution: RecipeSolution,
) {
    val display = solution.textDisplay(RecipesFirst)
    File("$pathPrefix.txt").apply {
        parentFile.mkdirs()
        writeText(display)
    }
    println(display)

    val dotFile = File("$pathPrefix.dot")
    val graph = solution.toThroughputGraph {
        mergeItemsByQuality()
        mergeRecipesByQuality()
    }.toFancyDotGraph()
    dotFile.writeDotGraph(graph)
}

val module1s = SpaceAge.run {
    listOf(
        speedModule,
        productivityModule,
        qualityModule
    )
}
val module2s = SpaceAge.run {
    listOf(
        speedModule2,
        productivityModule2,
        qualityModule2
    )
}
val module3s = SpaceAge.run {
    listOf(
        speedModule3,
        productivityModule3,
        qualityModule3
    )
}
val module12s = module1s + module2s
val module12sAllQualities = module12s.flatMap { SpaceAge.qualities.map { q -> it.withQuality(q) } }
val module123AllQualities =
    module12sAllQualities + module3s.flatMap { SpaceAge.qualities.map { q -> it.withQuality(q) } }

val speed2Beacons = SpaceAge.run {
    listOf<WithBeaconCount>(
        beacon(fill = speedModule2, sharing = 6.0),
        beacon(fill = speedModule2, sharing = 6.0) * 2,
        beacon(fill = speedModule2, sharing = 4.0) * 3
    )
}
val speed3Beacons = SpaceAge.run {
    listOf<WithBeaconCount>(
        beacon(fill = speedModule3, sharing = 6.0),
        beacon(fill = speedModule3, sharing = 6.0) * 2,
        beacon(fill = speedModule3, sharing = 4.0) * 3
    )
}

fun ProblemBuilder.CostsScope.addQualityCosts(
    item: Item,
    baseCost: Double,
    multipliers: List<Double>,
) {
    var curCost = baseCost
    for ((mult, quality) in (listOf(1.0) + multipliers).zip(prototypes.qualities)) {
        curCost *= mult
        costOf(item.withQuality(quality), curCost)
    }
}
