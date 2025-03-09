package scripts.vulcanus

import glassbricks.factorio.recipes.SpaceAge
import glassbricks.factorio.recipes.export.FactorioShorthandFormatter
import glassbricks.factorio.recipes.problem.factory
import glassbricks.factorio.recipes.problem.problem
import glassbricks.recipeanalysis.lp.LpOptions
import glassbricks.recipeanalysis.perSecond
import glassbricks.recipeanalysis.recipelp.textDisplay
import scripts.*

fun main() = with(SpaceAge) {
    val vulcanusFactory = factory {
        vulcanusMachines()
        recipes {
            default {
                allQualities()
            }
            allCraftingRecipes()
            calciteMining {
                cost = 30.0
            }
            coalMining()
        }
    }

    val production = vulcanusFactory.problem {
        input(lava, cost = 0.0)
        input(sulfuricAcid, cost = 0.0005)

        output(ironPlate.withQuality(legendary), 1.0.perSecond)

        costs {
            vulcanusMachineCosts1()
            vulcanusModuleCosts1()
            forbidAllUnspecified()
        }
    }
    val solution = production.solve(
        options = LpOptions(epsilon = 1e-5)
    )
    println(solution.status)
    println(solution.solution?.textDisplay(FactorioShorthandFormatter))
}
