package glassbricks.factorio.recipes.problem

import glassbricks.factorio.recipes.RecipeOrResource
import glassbricks.factorio.recipes.maybeWithQuality
import glassbricks.recipeanalysis.Ingredient
import glassbricks.recipeanalysis.recipelp.PseudoProcess
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet

interface AbstractRecipe<T> {
    val inputs: Collection<T>
    val outputs: Collection<T>
}

fun <T, A : AbstractRecipe<T>> findProducibleRecipes(
    recipes: Iterable<A>,
    startingItems: Iterable<T>,
): Pair<Set<T>, List<A>> {
    val producibleItems = ObjectOpenHashSet<T>()
    val producibleRecipes = ArrayList<A>()

    class RecipeNode(val recipe: A, var inDeg: Int)

    val itemQueue = ArrayDeque<T>()
    fun markItem(item: T) {
        if (producibleItems.add(item)) {
            itemQueue.add(item)
        }
    }
    startingItems.forEach { markItem(it) }

    fun markRecipe(node: RecipeNode) {
        node.recipe.outputs.forEach { markItem(it) }
        producibleRecipes.add(node.recipe)
    }

    val nodesByInput: MutableMap<T, MutableList<RecipeNode>> = Object2ObjectOpenHashMap()
    for (item in recipes) {
        val inputs = item.inputs
        val node = RecipeNode(item, inputs.size)
        for (input in inputs) {
            nodesByInput.getOrPut(input, ::ArrayList).add(node)
        }
        if (node.inDeg == 0) {
            markRecipe(node)
        }
    }

    while (itemQueue.isNotEmpty()) {
        val item = itemQueue.removeFirst()
        for (node in nodesByInput[item].orEmpty()) {
            if (--node.inDeg == 0) markRecipe(node)
        }
    }

    return producibleItems to producibleRecipes
}

/**
 * Also returns the set of producible items (so it can later be verified against expected outputs).
 */
fun FactoryConfig.removeUnusableRecipes(
    inputItems: List<Ingredient>,
    customProcesses: List<PseudoProcess>,
): Pair<FactoryConfig, Set<Ingredient>> {

    // first, find recipes that actually have machines
    val craftingCategories = machines.keys.flatMap { it.craftingCategories }.toSet()
    val craftableRecipes = recipes.keys.filter { it.craftingCategory in craftingCategories }

    class Recipe(
        override val inputs: Collection<Ingredient>,
        override val outputs: Collection<Ingredient>,
        val recipe: RecipeOrResource<*>?,
    ) : AbstractRecipe<Ingredient>

    val allRecipes = craftableRecipes.map { Recipe(it.inputs.keys, it.outputs.keys, it) } +
            customProcesses.map {
                val (inputs, outputs) = it.ingredientRate.partition { it.doubleValue < 0 }
                Recipe(inputs.map {
                    it.key.maybeWithQuality(prototypes.defaultQuality)
                }, outputs.map {
                    it.key.maybeWithQuality(prototypes.defaultQuality)
                }, null)
            }

    val (items, producibleRecipes) = findProducibleRecipes(
        allRecipes,
        inputItems,
    )
    val newFactory = copy(recipes = producibleRecipes.mapNotNull { it.recipe }.associateWith { this.recipes[it]!! })
    return newFactory to items
}
