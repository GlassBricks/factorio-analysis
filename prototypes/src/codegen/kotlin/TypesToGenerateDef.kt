package glassbricks.factorio.prototypecodegen

import com.squareup.kotlinpoet.*
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
                    FunSpec.builder("toString").addModifiers(KModifier.OVERRIDE).returns(String::class)
                        .addStatement("return \"\${this::class.simpleName}(\$name)\"").build()
                )
            }
        }
        "Prototype" {}
        "EntityPrototype" {
            +"placeable_by"
            +"minable"
        }
        "EntityWithHealthPrototype" {}
        "EntityWithOwnerPrototype" {}

        fun blueprintable(
            name: String, block: GeneratedPrototypeBuilder.() -> Unit = {}
        ) {
            prototype(name + "Prototype") {
                tryAddProperty("energy_source")
                tryAddProperty("allowed_effects")
                tryAddProperty("base_productivity")
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
        }
        "ModulePrototype" {
            +"category"
            +"tier"
            +"effect"
        }
        "ModuleCategory" {}
        val origPrototypes = this@classesToGenerate.origPrototypes
        for (prototype in getAllPrototypeSubclasses(origPrototypes, "ItemPrototype")) {
            if (prototype.name in this@classesToGenerate.prototypes) continue
            prototype(prototype.name) {}
        }

        // fluid
        "FluidPrototype" {}

        // recipes
        "RecipePrototype" {
            +"category"
            +"ingredients"
            +"results"
            +"main_product"
            +"maximum_productivity"
            +"surface_conditions"
            +"allowed_module_categories"
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

    allSubclassGetters = listOf("ItemPrototype")

    val hasEnergySource = getAllPrototypeSubclasses(origPrototypes, "EntityWithOwnerPrototype").filter {
        it.name in this.prototypes && it.properties.any { prop ->
            prop.name == "energy_source"
        }
    }

    extraSealedIntf("HasEnergySource", emptyList(), *hasEnergySource.map { it.name }.toTypedArray()) {
        // val energy_source: EnergySource
        addProperty(
            PropertySpec.builder(
                "energy_source", ClassName(PACKAGE_NAME, "EnergySource")
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
