import glassbricks.factorio.recipes.*
import glassbricks.recipeanalysis.LpOptions
import glassbricks.recipeanalysis.OrToolsLp
import glassbricks.recipeanalysis.Time
import glassbricks.recipeanalysis.div
import java.io.File
import kotlin.time.Duration.Companion.minutes

fun main() {
    val vulcanusFactory = SpaceAge.factory {
        machines {
            default {
                includeBuildCosts()

                moduleConfig()

                val beacons = listOf(
                    speedModule,
                    speedModule2
                ).flatMap { module ->
                    qualities.map { q ->
                        beacon(fill = module.withQuality(q), sharing = 6.0)
                    }
                }.map {
                    listOf(it)
                }.plusElement(emptyList())

                val modules = listOf(
                    speedModule,
                    speedModule2,
                    productivityModule,
                    productivityModule2,
                    qualityModule,
                    qualityModule2
                )

                for (q in listOf(normal)) {
                    for (module in modules) {
                        for (beacon in beacons) {
                            moduleConfig(fill = module.withQuality(q), beacons = beacon)
                        }
                    }
                }

            }
            chemicalPlant()
            oilRefinery()
            assemblingMachine3()
            foundry()
            recycler() // not integral as we can easily share recyclers
            electromagneticPlant()
            bigMiningDrill()
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
        input(sulfuricAcid, cost = 0.1)

        fun addWithQualities(item: Item, baseCost: Double) {
            input(item, cost = baseCost)
            input(item.withQuality(uncommon), cost = baseCost * 5)
            input(item.withQuality(rare), cost = baseCost * 5 * 5)
            input(item.withQuality(epic), cost = baseCost * 5 * 5 * 5)
            input(item.withQuality(legendary), cost = baseCost * 5 * 5 * 5 * 5)
        }
        addWithQualities(holmiumPlate, 100.0)
        addWithQualities(supercapacitor, 350.0)

        output(
            mechArmor.withQuality(legendary),
            rate = 1.0 / Time(60.0 * 60.0),
        )

        costs {
            costOf(assemblingMachine3.item(), 3)
            costOf(foundry.item(), 5)
            costOf(recycler.item(), 10)
            costOf(electromagneticPlant.item(), 50)
            costOf(beacon.item(), 4)

            fun addQualityCost(item: Item, baseCost: Double) {
                costOf(item, baseCost)
                costOf(item.withQuality(uncommon), baseCost * 5)
                costOf(item.withQuality(rare), baseCost * 5 * 5)
                costOf(item.withQuality(epic), baseCost * 5 * 5 * 5)
                costOf(item.withQuality(legendary), baseCost * 5 * 5 * 5 * 5)
            }
            addQualityCost(qualityModule2, 5.0)

            costOf(speedModule, 1)
            costOf(speedModule2, 5)
            costOf(productivityModule2, 5)
        }

        lpOptions = LpOptions(
            solver = OrToolsLp("CLP"),
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
