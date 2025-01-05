package glassbricks.factorio.prototypecodegen

import com.squareup.kotlinpoet.*
import kotlinx.serialization.Serializable


fun GeneratedPrototypesBuilder.classesToGenerate() {
    builtins = mutableMapOf(
        "bool" to Boolean::class.asClassName(),
        "double" to Double::class.asClassName(),
        "float" to Float::class.asClassName(),
        "int8" to Byte::class.asClassName(),
        "int16" to Short::class.asClassName(),
        "int32" to Int::class.asClassName(),
        "string" to String::class.asClassName(),
        "uint8" to UByte::class.asClassName(),
        "uint16" to UShort::class.asClassName(),
        "uint32" to UInt::class.asClassName(),
        "uint64" to ULong::class.asClassName(),
    )
    predefined = mutableMapOf(
        "BoundingBox" to ClassName(PAR_PACKAGE_NAME, "BoundingBox"),
        "Direction" to ClassName(PAR_PACKAGE_NAME, "Direction"),
    )

    extraSealedIntf("EVEnergySource", listOf("EnergySource"), "ElectricEnergySource", "VoidEnergySource")
    extraSealedIntf("BVEnergySource", listOf("EnergySource"), "BurnerEnergySource", "VoidEnergySource")
    extraSealedIntf(
        "EHFVEnergySource",
        listOf("EnergySource"),
        "ElectricEnergySource",
        "HeatEnergySource",
        "FluidEnergySource",
        "VoidEnergySource"
    )

    prototypes {
        "PrototypeBase" {
            +"type"
            +"name"

            modify = {
                addFunction(
                    FunSpec.builder("toString")
                        .addModifiers(KModifier.OVERRIDE)
                        .returns(String::class)
                        .addStatement("return \"\${this::class.simpleName}(\$name)\"")
                        .build()
                )
            }
        }
        "EntityPrototype" {
            +"collision_box"
            +"collision_mask"
            +"flags"
            +"build_grid_size"
            +"placeable_by"
            +"tile_width"
            +"tile_height"
        }
        "EntityWithHealthPrototype" {}
        "EntityWithOwnerPrototype" {}

        fun blueprintable(
            name: String,
            block: GeneratedPrototypeBuilder.() -> Unit = {}
        ) {
            prototype(name + "Prototype") {
                tryAddProperty("energy_source")
                tryAddProperty("fluid_box")
                tryAddProperty("output_fluid_box")
                tryAddProperty("circuit_wire_max_distance")
                tryAddProperty("allowed_effects")
                tryAddProperty("base_productivity")
                tryAddProperty("module_specification")
                block()
            }
        }

        blueprintable("Accumulator") {
            +"default_output_signal"
        }
        blueprintable("ArtilleryTurret")
        blueprintable("Beacon") {
            +"supply_area_distance"
            +"distribution_effectivity"
        }
        blueprintable("Boiler")
        blueprintable("BurnerGenerator") {
            +"burner"
        }
        blueprintable("Combinator")
        blueprintable("ArithmeticCombinator")
        blueprintable("DeciderCombinator")
        blueprintable("ConstantCombinator") {
            +"item_slot_count"
        }
        blueprintable("Container") {
            +"inventory_size"
            "inventory_type" {
                innerEnumName = "InventoryType"
            }
        }
        blueprintable("LogisticContainer") {
            "logistic_mode" {
                innerEnumName = "LogisticMode"
                inner.optional = true
            }
            +"max_logistic_slots"

        }
        blueprintable("InfinityContainer")
        blueprintable("CraftingMachine") {
            +"crafting_speed"
            +"crafting_categories"
            +"fluid_boxes"
        }
        blueprintable("AssemblingMachine") {
            +"fixed_recipe"
            +"ingredient_count"
        }
        blueprintable("RocketSilo")
        blueprintable("Furnace")
        blueprintable("ElectricEnergyInterface")
        blueprintable("ElectricPole") {
            +"supply_area_distance"
            +"maximum_wire_distance"
        }
        blueprintable("Gate")
        blueprintable("Generator")
        blueprintable("HeatInterface")
        blueprintable("HeatPipe")
        blueprintable("Inserter") {
            +"insert_position"
            +"pickup_position"
            +"filter_count"
            +"default_stack_control_input_signal"
        }
        blueprintable("Lab") {
            +"inputs"
        }
        blueprintable("Lamp")
        blueprintable("LandMine")
        blueprintable("LinkedContainer") {
            +"inventory_size"
            "inventory_type" {
                innerEnumName = "InventoryType"
            }
        }
        blueprintable("MiningDrill") {
            +"vector_to_place_result"
            +"resource_categories"
        }
        blueprintable("OffshorePump") {
            +"fluid"
        }
        blueprintable("Pipe")
        blueprintable("InfinityPipe")
        blueprintable("PipeToGround")
        blueprintable("PlayerPort")
        blueprintable("PowerSwitch")
        blueprintable("ProgrammableSpeaker") {
            +"maximum_polyphony"
            +"instruments"
        }
        blueprintable("Pump")
        blueprintable("Radar") {
            +"max_distance_of_sector_revealed"
            +"max_distance_of_nearby_sector_revealed"
        }
        blueprintable("Rail")
        blueprintable("StraightRail")
        blueprintable("CurvedRail")
        blueprintable("RailSignalBase") {
            +"default_red_output_signal"
            +"default_orange_output_signal"
            +"default_green_output_signal"
        }
        blueprintable("RailChainSignal") {
            +"default_blue_output_signal"
        }
        blueprintable("RailSignal")
        blueprintable("Reactor")
        blueprintable("Roboport") {
            +"logistics_radius"
            +"construction_radius"
            +"default_available_logistic_output_signal"
            +"default_total_logistic_output_signal"
            +"default_available_construction_output_signal"
            +"default_total_construction_output_signal"
            +"logistics_connection_distance"
        }
        blueprintable("SimpleEntityWithOwner")
        blueprintable("SimpleEntityWithForce")
        blueprintable("SolarPanel")
        blueprintable("StorageTank") {
            +"two_direction_only"
        }
        blueprintable("TrainStop") {
            +"default_train_stopped_signal"
            +"default_trains_count_signal"
            +"default_trains_limit_signal"
        }
        blueprintable("TransportBeltConnectable") {
            +"speed"
        }
        blueprintable("LinkedBelt") {
            +"allow_clone_connection"
            +"allow_blueprint_connection"
            +"allow_side_loading"
        }
        blueprintable("Loader") {
            properties.remove("energy_source")
            "energy_source" {
                inner.default = ManualDefault(CodeBlock.of("VoidEnergySource"))
            }
            +"filter_count"
        }
        blueprintable("Loader1x1")
        blueprintable("Loader1x2")
        blueprintable("Splitter")
        blueprintable("TransportBelt") {
            +"related_underground_belt"
        }
        blueprintable("UndergroundBelt") {
            +"max_distance"
        }
        blueprintable("Turret")
        blueprintable("AmmoTurret")
        blueprintable("ElectricTurret")
        blueprintable("FluidTurret")
        blueprintable("Vehicle")
        blueprintable("RollingStock") {
            +"allow_manual_color"
        }
        blueprintable("ArtilleryWagon")
        blueprintable("CargoWagon") {
            +"inventory_size"
        }
        blueprintable("FluidWagon") {
            +"tank_count"
        }
        blueprintable("Locomotive")

        blueprintable("Wall")

        // items
        "ItemPrototype" {
            +"stack_size"
            +"place_result"
            +"fuel_category"
            +"flags"
        }
        "ModulePrototype" {
            +"category"
            +"tier"
            +"effect"
            +"limitation"
            +"limitation_blacklist"
        }
        val origPrototypes = this@classesToGenerate.origPrototypes
        fun isItemPrototype(name: String): Boolean {
            if (name == "ItemPrototype") return true
            val prototype = origPrototypes[name] ?: return false
            val parent = prototype.parent ?: return false
            return isItemPrototype(parent)
        }
        for (prototype in origPrototypes.values) {
            if (isItemPrototype(prototype.name) && prototype.name !in this@classesToGenerate.prototypes) {
                prototype(prototype.name) {}
            }
        }
    }

    concepts {
        "ItemID" {}
        "EntityID" {}
        "ItemToPlace" {}
        "ItemCountType" {}

        "Vector" {
            overrideType(ClassName(PAR_PACKAGE_NAME, "Vector"))
        }
        "MapPosition" {
            overrideType(ClassName(PAR_PACKAGE_NAME, "Position"))
        }

        "CollisionMaskLayer" {}

        this@classesToGenerate.predefined["CollisionMask"] = ClassName(PACKAGE_NAME, "CollisionMask")

        "EntityPrototypeFlags" {
            innerEnumName = "EntityPrototypeFlag"
        }

        "ItemPrototypeFlags" {
            innerEnumName = "ItemPrototypeFlag"
        }

        "SignalIDConnector" {
            overrideType(ClassName(PAR_PACKAGE_NAME, "SignalID"))
        }

        "ItemStackIndex" {}
        "ModuleSpecification" {
            includeAllProperties = false
            +"module_slots"
        }
        "EffectTypeLimitation" {
            innerEnumName = "EffectType"
        }

        "ModuleCategoryID" {}
        "RecipeID" {}
        "RecipeCategoryID" {}
        "ResourceCategoryID" {}
        "ProductionType" {}

        "Effect" {}
        "EffectValue" {}

        "FluidID" {}
        "FluidBox" {
            includeAllProperties = false
            +"pipe_connections"
            +"filter"
            +"production_type"
        }
        "PipeConnectionDefinition" {
            "type" {
                innerEnumName = "InputOutputType"
            }
        }

        "FuelCategoryID" {}
        "HeatConnection" {}

        // some stuff with energy source is hardcoded in Generate.kt
        "EnergySource" {
            overrideType = {
                val className = ClassName(PACKAGE_NAME, "EnergySource")
                val declaration = TypeSpec.interfaceBuilder(className).apply {
                    addDescription(concept.description)
                    addModifiers(KModifier.SEALED)
                    addAnnotation(Serializable::class)
                }.build()
                className to declaration
            }
        }
        "VoidEnergySource" {}
        "BurnerEnergySource" {
            includeAllProperties = false
            +"type"
            +"fuel_category"
            +"fuel_categories"
        }
        "HeatEnergySource" {
            includeAllProperties = false
            +"type"
            +"connections"
        }
        "FluidEnergySource" {
            includeAllProperties = false
            +"type"
            +"fluid_box"
        }
        "ElectricEnergySource" {
            includeAllProperties = false
            +"type"
        }

        "ProgrammableSpeakerInstrument" {}
        "ProgrammableSpeakerNote" {
            includeAllProperties = false
            +"name"
        }
    }

    allSubclassGetters += listOf(
        "ItemPrototype",
        "EntityWithOwnerPrototype",
    )

    val hasEnergySource = getAllPrototypeSubclasses(origPrototypes, "EntityWithOwnerPrototype")
        .filter {
            it.name in this.prototypes &&
                    it.properties.any { prop ->
                        prop.name == "energy_source"
                    }
        }

    extraSealedIntf("HasEnergySource", emptyList(), *hasEnergySource.map { it.name }.toTypedArray()) {
        // val energy_source: EnergySource
        addProperty(
            PropertySpec.builder(
                "energy_source",
                ClassName(PACKAGE_NAME, "EnergySource")
//                    .copy(nullable = true)
            ).build()
        )
    }

    for (prototype in hasEnergySource) {
        this.prototypes[prototype.name]!!.includedProperties["energy_source"]!!.modify = {
            addModifiers(KModifier.OVERRIDE)
        }
    }
}
