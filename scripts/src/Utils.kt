import glassbricks.factorio.recipes.SpaceAge
import glassbricks.factorio.recipes.export.RecipesFirst
import glassbricks.factorio.recipes.export.mergeItemsByQuality
import glassbricks.factorio.recipes.export.mergeRecipesByQuality
import glassbricks.factorio.recipes.export.toFancyDotGraph
import glassbricks.recipeanalysis.recipelp.ProductionStage
import glassbricks.recipeanalysis.recipelp.RecipeSolution
import glassbricks.recipeanalysis.recipelp.textDisplay
import glassbricks.recipeanalysis.recipelp.toThroughputGraph
import glassbricks.recipeanalysis.writeDotGraph
import java.io.File

fun printAndExportStagedSolution(
    pathName: String,
    solution: Map<ProductionStage, RecipeSolution>,
) {
    val dotFilePath = File(pathName)
    dotFilePath.mkdirs()
    for ((stage, solution) in solution) {
        val display = solution.textDisplay(RecipesFirst.Companion)
        dotFilePath.resolve("${stage.name}.txt").writeText(display)

        println("Stage: $stage")
        println("-".repeat(100))
        println(display)
        println()

        val dotFile = dotFilePath.resolve("${stage.name}.dot")
        val graph = solution.toThroughputGraph {
            mergeItemsByQuality()
            mergeRecipesByQuality()
        }.toFancyDotGraph()
        dotFile.writeDotGraph(graph)

    }
}

fun printAndExportSolution(
    pathPrefix: String, solution: RecipeSolution,
) {
    val display = solution.textDisplay(RecipesFirst.Companion)
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
val module12s = module1s + module2s
val module12sAllQualities = module12s.flatMap { SpaceAge.qualities.map { q -> it.withQuality(q) } }
