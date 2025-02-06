package glassbricks.recipeanalysis.recipelp

import glassbricks.recipeanalysis.DirectedHashGraph
import glassbricks.recipeanalysis.Graph
import glassbricks.recipeanalysis.Ingredient

sealed interface ThroughputGraphNode

data class ThroughputNode(val ingredient: Ingredient) : ThroughputGraphNode
data class RecipeNode(val process: PseudoProcess) : ThroughputGraphNode

fun RecipeLpSolution.toThroughputGraph(): Graph<ThroughputGraphNode, Double> {
    val graph = DirectedHashGraph<ThroughputGraphNode, Double>()
    for ((recipe, usage) in processUsage) {
        val recipeNode = RecipeNode(recipe)
        graph.addNode(recipeNode)
        val thisRate = recipe.ingredientRate * usage
        for ((ingredient, rate) in thisRate) {
            val ingredientNode = ThroughputNode(ingredient)
            graph.addNode(ingredientNode)
            if (rate < 0) {
                graph.addEdge(ingredientNode, recipeNode, -rate)
            } else {
                graph.addEdge(recipeNode, ingredientNode, rate)
            }
        }
    }
    return graph
}
