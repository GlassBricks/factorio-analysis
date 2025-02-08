import glassbricks.factorio.recipes.*
import glassbricks.recipeanalysis.recipelp.ProductionStage
import glassbricks.recipeanalysis.recipelp.RecipeSolution
import glassbricks.recipeanalysis.recipelp.textDisplay
import glassbricks.recipeanalysis.writeTo
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
        solution.toFancyDotGraph {
            clusterItemsByQuality()
            clusterRecipesByQuality()
            flipEdgesForMachine(SpaceAge.recycler)
        }.writeTo(dotFile)
    }
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
