import glassbricks.factorio.recipes.*
import glassbricks.recipeanalysis.*
import java.io.File
import kotlin.time.Duration.Companion.minutes

fun main() {
    val vulcanusFactory = SpaceAge.factory {
        machines {
            default {
                includeBuildCosts()

                moduleConfig(fill = qualityModule2)
                moduleConfig(fill = qualityModule2.withQuality(uncommon))
                moduleConfig(fill = qualityModule2.withQuality(rare))
                moduleConfig(fill = qualityModule2.withQuality(epic))
                moduleConfig(fill = qualityModule2.withQuality(legendary))
                moduleConfig()

                val beacon6 = beacon(fill = speedModule2, sharing = 6.0)

                for (q in qualities) {
                    moduleConfig(fill = speedModule.withQuality(q))
                    moduleConfig(fill = speedModule.withQuality(q), beacons = listOf(beacon6))
                    moduleConfig(fill = speedModule2.withQuality(q))
                    moduleConfig(fill = speedModule2.withQuality(q), beacons = listOf(beacon6))
                    moduleConfig(fill = qualityModule2.withQuality(q))
                    moduleConfig(fill = productivityModule2.withQuality(q), beacons = listOf(beacon6))
                    moduleConfig(fill = productivityModule2.withQuality(q), beacons = listOf(beacon6 * 4))

//                moduleConfig(fill = qualityModule3.withQuality(q))
                }

            }
            chemicalPlant()
            oilRefinery()
            assemblingMachine3 {
//                integral = true
            }

            foundry {
//                integral = true
            }
            recycler() // not integral as we can easily share recyclers
            electromagneticPlant {
//                integral = true
            }
        }
        recipes {
            default {
                allQualities()
            }
            allRecipes {}
        }
    }

    val production = vulcanusFactory.problem {
        input(coal, cost = 10.0)
        input(calcite, cost = 10.0)
        input(lava, cost = 0.0)
        input(sulfuricAcid, cost = 0.1)
        //        input(scrap, cost = 1.0)
//        input(heavyOil, cost = 0.0)
        fun addWithQualities(item: Item, baseCost: Double) {
//            input(item, cost = baseCost)
//            input(item.withQuality(uncommon), cost = baseCost * 5)
//            input(item.withQuality(rare), cost = baseCost * 5 * 5)
//            input(item.withQuality(epic), cost = baseCost * 5 * 5 * 5)
//            input(item.withQuality(legendary), cost = baseCost * 5 * 5 * 5 * 5)
        }
//        addWithQualities(holmiumPlate, 100.0
//        addWithQualities(supercapacitor, 350.0)

        val slot2x2 = Symbol("2x2 slot")
        val slot1x1 = Symbol("1x1 slot")
        output(
            mechArmor.withQuality(legendary),
            rate = 1.0 / Time(60.0 * 30.0),
        )

//        surplusCost = 1e-8

        costs {
            costOf(speedModule, 1)

            costOf(speedModule2, 5)
            costOf(productivityModule2, 5)

            costOf(assemblingMachine3.item(), 3)
            costOf(foundry.item(), 5)
            costOf(recycler.item(), 10)
            costOf(electromagneticPlant.item(), 50)

            fun addQualityCost(item: Item, baseCost: Double) {
                costOf(item, baseCost)
                costOf(item.withQuality(uncommon), baseCost * 5)
                costOf(item.withQuality(rare), baseCost * 5 * 5)
                costOf(item.withQuality(epic), baseCost * 5 * 5 * 5)
                costOf(item.withQuality(legendary), baseCost * 5 * 5 * 5 * 5)
            }
            addQualityCost(qualityModule2, 5.0)
//            limit(qualityModule2, 1)
//            addQualityCost(qualityModule3, 22.0)
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
