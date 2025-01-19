package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.MiningDrillPrototype

typealias AnyMiningDrill = AnyMachine<MiningDrillPrototype>

data class MiningDrill(
    override val prototype: MiningDrillPrototype,
    override val quality: Quality,
) : BaseMachine<MiningDrillPrototype>(), AnyMiningDrill {
    // higher quality miners don't mine faster
    override val baseCraftingSpeed: Double get() = prototype.mining_speed
    override fun withQuality(quality: Quality): MiningDrill = MiningDrill(prototype, quality)
}

typealias MiningDrillWithModules = MachineWithModules<MiningDrillPrototype>
