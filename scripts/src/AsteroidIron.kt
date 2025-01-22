import glassbricks.factorio.recipes.ResearchConfig
import glassbricks.factorio.recipes.SpaceAge
import glassbricks.factorio.recipes.problem.factory
import glassbricks.factorio.recipes.problem.problem
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
            allRecipes()
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

    val solution = production.solve()
    println(solution.status)
    val display = solution.recipeSolution?.display()
    println(display)
    display?.let {
        File("output/legendary-mech-armor.txt").also { it.parentFile.mkdirs() }.writeText(it)
    }
}
