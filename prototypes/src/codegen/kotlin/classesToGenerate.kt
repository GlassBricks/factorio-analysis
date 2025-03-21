package glassbricks.factorio.prototypecodegen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlinx.serialization.Serializable

fun GeneratedPrototypesBuilder.classesToGenerate() {
    builtins = mapOf(
        "bool" to Boolean::class.asClassName(),
        "float" to Float::class.asClassName(),
        "double" to Double::class.asClassName(),
        "string" to String::class.asClassName(),
        "int8" to Byte::class.asClassName(),
        "int16" to Short::class.asClassName(),
        "int32" to Int::class.asClassName(),
        "int64" to Long::class.asClassName(),
        "uint8" to UByte::class.asClassName(),
        "uint16" to UShort::class.asClassName(),
        "uint32" to UInt::class.asClassName(),
        "uint64" to ULong::class.asClassName(),
    )
    predefined = mapOf(
        "BoundingBox" to ClassName(PAR_PACKAGE_NAME, "BoundingBox"),
        "Direction" to ClassName(PAR_PACKAGE_NAME, "Direction"),
    )

    extraIntf("EVEnergySource", sealed = true, listOf("EnergySource"), "ElectricEnergySource", "VoidEnergySource")
    extraIntf("BVEnergySource", sealed = true, listOf("EnergySource"), "BurnerEnergySource", "VoidEnergySource")
    extraIntf(
        "EHFVEnergySource",
        sealed = true,
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
                    FunSpec.builder("toString").addModifiers(KModifier.OVERRIDE).returns(String::class)
                        .addStatement("return \"\${this::class.simpleName}(\$name)\"").build()
                )
            }
        }
        "Prototype" {}
        "EntityPrototype" {
            +"placeable_by"
            +"minable"
            +"tile_height"
            +"tile_width"
            +"collision_box"
        }
        "EntityWithHealthPrototype" {}
        "EntityWithOwnerPrototype" {}

        fun blueprintable(
            name: String, block: GeneratedPrototypeBuilder.() -> Unit = {},
        ) {
            prototype(name + "Prototype") {
                tryAddProperty("energy_source")
                tryAddProperty("energy_usage")
                tryAddProperty("allowed_effects")
                tryAddProperty("module_slots")
                tryAddProperty("allowed_module_categories")
                tryAddProperty("effect_receiver")
                block()
            }
        }

        blueprintable("AgriculturalTower")
        blueprintable("AsteroidCollector")
        blueprintable("Beacon") {
            +"supply_area_distance"
            +"distribution_effectivity"
            +"distribution_effectivity_bonus_per_quality_level"
            +"profile"
            "beacon_counter" {
                innerEnumName = "BeaconCounter"
            }
        }
        blueprintable("Boiler")
        blueprintable("CraftingMachine") {
            +"crafting_speed"
            +"crafting_categories"
            +"fluid_boxes"
        }
        blueprintable("AssemblingMachine") {
            +"fixed_recipe"
            +"fixed_quality"
            +"ingredient_count"
        }
        blueprintable("RocketSilo") {
            +"launch_to_space_platforms"
        }
        blueprintable("Furnace")
        blueprintable("Lab") {
            +"inputs"
            +"uses_quality_drain_modifier"
            +"science_pack_drain_rate_percent"
        }
        blueprintable("MiningDrill") {
            +"mining_speed"
            +"resource_categories"
        }
        blueprintable("OffshorePump") {
            +"pumping_speed"
        }
        blueprintable("Pump") {
            +"pumping_speed"
        }
        // items
        "ItemPrototype" {
            +"flags"
            +"place_result"
            +"stack_size"
            +"spoil_result"
            +"plant_result"
            +"spoil_ticks"
            +"fuel_value"
            +"fuel_category"
            +"weight"
            +"ingredient_to_weight_coefficient"
            +"default_import_location"
            +"place_as_equipment_result"
        }
        "ModulePrototype" {
            +"category"
            +"tier"
            +"effect"
        }
        "ModuleCategory" {}
        fun addAllSubtypes(name: String) {
            for (prototype in getAllPrototypeSubclasses(this@classesToGenerate.origPrototypes, name)) {
                if (prototype.name !in this@classesToGenerate.prototypes) {
                    prototype(prototype.name) {}
                }
            }
        }

        addAllSubtypes("ItemPrototype")

        // fluid
        "FluidPrototype" {}

        // equipment
        "EquipmentPrototype" {
            +"shape"
        }
        addAllSubtypes("EquipmentPrototype")
        // ignore equipmentGhost
        this@classesToGenerate.prototypes.remove("EquipmentGhostPrototype")

        // recipes
        "RecipePrototype" {
            +"category"
            +"ingredients"
            +"results"
            +"main_product"
            +"energy_required"
            +"maximum_productivity"
            +"surface_conditions"
            +"allowed_module_categories"
            +"allow_consumption"
            +"allow_speed"
            +"allow_productivity"
            +"allow_pollution"
            +"allow_quality"
        }
        "RecipeCategory" {}

        // space location
        "SpaceLocationPrototype" {}
        "PlanetPrototype" {
            +"surface_properties"
        }
        "SurfacePropertyPrototype" {
            +"default_value"
        }

        // quality
        "QualityPrototype" {
            +"level"
            +"next"
            +"next_probability"

            +"mining_drill_resource_drain_multiplier"
            +"science_pack_drain_multiplier"
        }

        // resource
        "ResourceCategory" {}
        "ResourceEntityPrototype" {
            +"category"
            +"infinite"
            +"minimum"
            +"normal"
        }

        // misc categories
        "FuelCategory" {}

    }

    concepts {
        for (prototype in this@classesToGenerate.prototypes) {
            val idName = prototype.key.removeSuffix("Prototype") + "ID"
            if (idName in this@classesToGenerate.origConcepts) {
                concept(idName) {
                    if (prototype.value.typeName != null) isIdTypeFor = prototype.key
                }
            }
        }

        "ItemStackIndex" {}
        "ItemPrototypeFlags" {
            innerEnumName = "ItemFlag"
        }
        "ItemCountType" {}

        "EffectTypeLimitation" {
            innerEnumName = "EffectType"
        }
        "EffectReceiver" {}

        "ItemToPlace" {}

        "Energy" {}
        "Weight" {}
        "FluidAmount" {}

        "MinableProperties" {
            includeAllProperties = false
            +"mining_time"
            +"results"
            +"result"
            +"count"
            +"required_fluid"
            +"fluid_amount"
        }

        "Effect" {}
        "EffectValue" {}

        "IngredientPrototype" {
            isSealedIntf = true
        }
        "ItemIngredientPrototype" {}
        "FluidIngredientPrototype" {}
        "ProductPrototype" {
            isSealedIntf = true
        }
        "ItemProductPrototype" {
            "amount" {
                inner.type = BasicType("double")
            }
        }
        "FluidProductPrototype" {}
        "ResearchProgressProductPrototype" {}

        "SurfaceCondition" {}

        "BurnerUsageID" {}

        "FluidBox" {
            includeAllProperties = false
            "pipe_connections" {}
            "filter" {}
            "production_type" {}
        }
        "ProductionType" {}
        "PipeConnectionDefinition" {
            includeAllProperties = false
            "flow_direction" {
                innerEnumName = "FlowDirection"
            }
        }

        "EquipmentShape" {
            "type" {
                innerEnumName = "EquipmentShapeType"
            }
        }

//        // some stuff with energy source is hardcoded in Generate.kt
        "EnergySource" {
            overrideType = run {
                val className = ClassName(PACKAGE_NAME, "EnergySource")
                val declaration = TypeSpec.interfaceBuilder(className).apply {
                    addDescription(concept.description)
                    addModifiers(KModifier.SEALED)
                    addAnnotation(Serializable::class)
                }.build()
                declaration
            }
        }
        "VoidEnergySource" {}
        "BurnerEnergySource" {
            includeAllProperties = false
            +"type"
            +"burner_usage"
            +"fuel_categories"
        }
        "HeatEnergySource" {
            includeAllProperties = false
            +"type"
        }
        "FluidEnergySource" {
            includeAllProperties = false
            +"type"
        }
        "ElectricEnergySource" {
            includeAllProperties = false
            +"type"
        }
    }

    allSubclassGetters = listOf("ItemPrototype", "CraftingMachinePrototype", "EquipmentPrototype")

    fun List<Prototype>.filterWithProperty(propName: String): List<Prototype> =
        filter { it.name in prototypes && it.properties.any { prop -> prop.name == propName } }

    val entityPrototypes = getAllPrototypeSubclasses(origPrototypes, "EntityWithOwnerPrototype")
    val intfs = extraIntf(
        name = "HasEnergySource",
        sealed = false,
        supertypes = emptyList(),
        subtypes = entityPrototypes.filterWithProperty("energy_source").map { it.name }) {
        addProperty(
            PropertySpec.builder("energy_source", ClassName(PACKAGE_NAME, "EnergySource")).build()
        )
    }

    for (prototype in intfs.subtypes) {
        this.prototypes[prototype]!!.includedProperties["energy_source"]!!.modify = {
            addModifiers(KModifier.OVERRIDE)
        }
    }

    val machineProps = mapOf(
        "effect_receiver" to ClassName(PACKAGE_NAME, "EffectReceiver").copy(nullable = true),
        "module_slots" to ClassName(PACKAGE_NAME, "ItemStackIndex").copy(nullable = true),
        "allowed_effects" to ClassName(PACKAGE_NAME, "EffectTypeLimitation").copy(nullable = true),
        "allowed_module_categories" to List::class.asClassName()
            .parameterizedBy(ClassName(PACKAGE_NAME, "ModuleCategoryID")).copy(nullable = true),
        "energy_usage" to ClassName(PACKAGE_NAME, "Energy"),
    ).mapValues {
        PropertySpec.builder(it.key, it.value)
            .build()
    }
    val machineIntf = extraIntf(
        name = "MachinePrototype",
        sealed = false,
        supertypes = listOf("HasEnergySource"),
        "CraftingMachinePrototype", "MiningDrillPrototype"
    ) {
        for ((_, prop) in machineProps) {
            addProperty(prop)
        }
    }
    for (name in machineIntf.subtypes) {
        val prototype = origPrototypes[name]!!
        for (prop in machineProps.values) {
            this.prototypes[name]!!.includedProperties[prop.name]!!.modify = {
                addModifiers(KModifier.OVERRIDE)
            }
        }
    }

}
