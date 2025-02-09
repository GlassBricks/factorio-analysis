package glassbricks.factorio.recipes.problem

import glassbricks.factorio.recipes.MachineSetup
import glassbricks.factorio.recipes.Recipe
import glassbricks.recipeanalysis.recipelp.PseudoProcess
import glassbricks.recipeanalysis.recipelp.RealProcess

fun PseudoProcess.machine(): MachineSetup<*>? =
    (this as? RealProcess)?.process as? MachineSetup<*>

fun PseudoProcess.recipe(): Recipe? = machine()?.recipe as? Recipe
