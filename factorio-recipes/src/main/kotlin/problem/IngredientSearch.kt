package glassbricks.factorio.recipes.problem

import glassbricks.factorio.recipes.FactorioPrototypes
import glassbricks.factorio.recipes.MachineRecipe
import glassbricks.factorio.recipes.PseudoRecipe
import glassbricks.factorio.recipes.maybeWithQuality
import glassbricks.recipeanalysis.Ingredient
import glassbricks.recipeanalysis.IngredientRate
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

fun toAbstractRecipe(prototypes: FactorioPrototypes, ingredientRate: IngredientRate): AbstractRecipe<Ingredient> {
    val (inputEntries, outputEntries) = ingredientRate.partition { it.doubleValue < 0 }
    return object : AbstractRecipe<Ingredient> {
        override val inputs = inputEntries.map { it.key.maybeWithQuality(prototypes.defaultQuality) }
        override val outputs = outputEntries.map { it.key.maybeWithQuality(prototypes.defaultQuality) }
    }
}

/**
 * Also returns the set of producible items (so it can later be verified against expected outputs).
 */
fun Factory.removeUnusableRecipes(
    inputItems: List<Ingredient>,
    otherProcessesNoQuality: List<AbstractRecipe<Ingredient>>,
): Pair<Factory, Set<Ingredient>> {

    // Filter recipes that actually have machines
    val craftingCategories = machinesUsed().flatMap { it.craftingCategories }.toSet()
    val craftableRecipes = recipesUsed().filter {
        when {
            it is MachineRecipe<*> -> it.craftingCategory in craftingCategories
            else -> true
        }
    }

    class Recipe(
        override val inputs: Collection<Ingredient>,
        override val outputs: Collection<Ingredient>,
        val recipe: PseudoRecipe,
    ) : AbstractRecipe<Ingredient>

    val allRecipes = craftableRecipes.map { recipe ->
        Recipe(recipe.netInputs, recipe.netOutputs, recipe)
    } + otherProcessesNoQuality

    val (items, producibleRecipes) = findProducibleRecipes(
        allRecipes,
        inputItems,
    )
    val baseRecipes = producibleRecipes
        .filterIsInstance<Recipe>()
        .map { it.recipe }
        .toSet()

    return (this.filterMachineRecipes { it in baseRecipes }) to items
}
