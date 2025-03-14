@file:Suppress("unused")

package scripts

import glassbricks.factorio.recipes.*
import glassbricks.factorio.recipes.export.*
import glassbricks.factorio.recipes.problem.MachineConfigBuilder
import glassbricks.factorio.recipes.problem.ProblemBuilder
import glassbricks.recipeanalysis.recipelp.*
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
    pathPrefix: String,
    solution: RecipeSolution,
    formatter: FactorioRecipesFormatter = RecipesFirst,
) {
    val display = solution.textDisplay(formatter)
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

fun printAndExportSolution(
    pathPrefix: String,
    result: RecipeResult,
    formatter: FactorioRecipesFormatter = RecipesFirst,
) {
    println(result.status)
    result.solution?.let { printAndExportSolution(pathPrefix, it, formatter) }
}

val module1s = SpaceAge.run {
    listOf(
        speedModule,
        productivityModule,
        qualityModule,
        efficiencyModule
    )
}
val module2s = SpaceAge.run {
    listOf(
        speedModule2,
        productivityModule2,
        qualityModule2,
        efficiencyModule2
    )
}
val module3s = SpaceAge.run {
    listOf(
        speedModule3,
        productivityModule3,
        qualityModule3,
        efficiencyModule3
    )
}
val module12sAllQualities = (module1s + module2s).flatMap { SpaceAge.qualities.map { q -> it.withQuality(q) } }

val nonEffModulesAllQualities = SpaceAge.run {
    (module1s + module2s + module3s)
        .filter { !it.prototype.name.startsWith("efficiency") }
        .flatMap { qualities.map { q -> it.withQuality(q) } }
}

data class BeaconProfile(
    val sharing: Double,
    val count: Int,
)

val defaultBeaconProfiles = listOf(
    BeaconProfile(6.0, 1),
    BeaconProfile(6.0, 2),
    BeaconProfile(6.0, 3),
    BeaconProfile(6.0, 4),
    BeaconProfile(4.0, 8),
    BeaconProfile(2.0, 12),
)

fun beaconsWithSharing(
    module: Module,
    profiles: List<BeaconProfile> = defaultBeaconProfiles,
): List<BeaconCount> = profiles.map { (sharing, count) ->
    (SpaceAge.beacon)(fill = module, sharing = sharing) * count
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

fun MachineConfigBuilder.moduleConfigWithBeacons(
    modules: Iterable<Module>,
    beacons: List<List<WithBeaconCount>>,
) {
    for (module in modules) {
        moduleConfig(fill = module)
        if (module.effects.quality <= 0) {
            for (beaconConfig in beacons) {
                moduleConfig(beacons = beaconConfig)
                moduleConfig(fill = module, beacons = beaconConfig)
            }
        }
    }
}
