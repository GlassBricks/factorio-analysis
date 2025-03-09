package glassbricks.factorio.recipes.problem

import glassbricks.factorio.recipes.MachineProcess
import glassbricks.factorio.recipes.Recipe
import glassbricks.recipeanalysis.recipelp.PseudoProcess
import glassbricks.recipeanalysis.recipelp.RealProcess

fun PseudoProcess.machine(): MachineProcess<*>? =
    (this as? RealProcess)?.process as? MachineProcess<*>

fun PseudoProcess.recipe(): Recipe? = machine()?.recipe as? Recipe
