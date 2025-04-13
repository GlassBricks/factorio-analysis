package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.FuelCategoryID
import glassbricks.factorio.prototypes.RecipeID

data class ResearchConfig(
    val maxQuality: Quality? = null,
    val recipeProductivity: Map<RecipeID, Double> = emptyMap(),
    val miningProductivity: Double = 0.0,
    val fuels: Map<FuelCategoryID, Item> = emptyMap(),
)
