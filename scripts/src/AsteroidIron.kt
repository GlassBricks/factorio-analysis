import glassbricks.factorio.recipes.ResearchConfig
import glassbricks.factorio.recipes.SpaceAge
import glassbricks.factorio.recipes.export.FactorioShorthandFormatter
import glassbricks.factorio.recipes.problem.factory
import glassbricks.factorio.recipes.problem.problem
import glassbricks.recipeanalysis.recipelp.textDisplay
import java.io.File

fun main() {
    val space = SpaceAge.factory {
        machines {
            default {
                includeBuildCosts()
                moduleConfig(fill = productivityModule2.withQuality(rare))
                moduleConfig(fill = qualityModule2.withQuality(rare))
                moduleConfig(fill = speedModule2)
            }
            crusher()
            electricFurnace()
        }
        researchConfig = ResearchConfig(
            miningProductivity = 0.2
        )
        recipes {
            default {
                allQualities()
            }
            allCraftingRecipes()
        }
    }

    val production = space.problem {
        input(carbonicAsteroidChunk)
        maximize(ironPlate.withQuality(epic))

        costs {
            limit(crusher.item(), 60)
        }
//        lpOptions = LpOptions(
//            solver = OrToolsLp("CLP"),
//            timeLimit = 15.minutes,
//            epsilon = 1e-5
//        )
    }

    val result = production.solve()
    println(result.status)
    val display = result.solution?.textDisplay(FactorioShorthandFormatter)
    println(display)
    display?.let {
        File("output/legendary-mech-armor.txt").also { it.parentFile.mkdirs() }.writeText(it)
    }
}
