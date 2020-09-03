package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.model.LocalConstructorInformation
import net.corda.serialization.internal.model.LocalPropertyInformation
import net.corda.serialization.internal.model.LocalTypeInformation
import net.corda.serialization.internal.model.RemoteTypeInformation
import net.corda.serialization.internal.model.TypeIdentifier
import java.io.NotSerializableException
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

private const val IGNORE_COMPUTED = -1

/**
 * Creates a new [ObjectBuilder] ready to be populated with deserialized values so that it can create an object instance.
 *
 * @property propertySlots The slot indices of the properties written by the provided [ObjectBuilder], by property name.
 * @param provider The thunk that provides a new, empty [ObjectBuilder]
 */
data class ObjectBuilderProvider(
        val propertySlots: Map<String, Int>,
        private val provider: () -> ObjectBuilder
) : () -> ObjectBuilder by provider

/**
 * Wraps the operation of calling a constructor, with helpful exception handling.
 */
private class ConstructorCaller(private val javaConstructor: Constructor<Any>) : (Array<Any?>) -> Any {

    override fun invoke(parameters: Array<Any?>): Any =
            try {
                javaConstructor.newInstance(*parameters)
            } catch (e: InvocationTargetException) {
                @Suppress("DEPRECATION")    // JDK11: isAccessible() should be replaced with canAccess() (since 9)
                throw NotSerializableException(
                        "Constructor for ${javaConstructor.declaringClass} (isAccessible=${javaConstructor.isAccessible}) " +
                                "failed when called with parameters ${parameters.toList()}: ${e.cause!!.message}"
                )
            } catch (e: IllegalAccessException) {
                @Suppress("DEPRECATION")    // JDK11: isAccessible() should be replaced with canAccess() (since 9)
                throw NotSerializableException(
                        "Constructor for ${javaConstructor.declaringClass} (isAccessible=${javaConstructor.isAccessible}) " +
                                "not accessible: ${e.message}"
                )
            }
}

/**
 * Wraps the operation of calling a setter, with helpful exception handling.
 */
private class SetterCaller(val setter: Method) : (Any, Any?) -> Unit {
    override fun invoke(target: Any, value: Any?) {
        try {
            setter.invoke(target, value)
        } catch (e: InvocationTargetException) {
            @Suppress("DEPRECATION")    // JDK11: isAccessible() should be replaced with canAccess() (since 9)
            throw NotSerializableException(
                    "Setter ${setter.declaringClass}.${setter.name} (isAccessible=${setter.isAccessible} " +
                            "failed when called with parameter $value: ${e.cause!!.message}"
            )
        } catch (e: IllegalAccessException) {
            @Suppress("DEPRECATION")    // JDK11: isAccessible() should be replaced with canAccess() (since 9)
            throw NotSerializableException(
                    "Setter ${setter.declaringClass}.${setter.name} (isAccessible=${setter.isAccessible} " +
                            "not accessible: ${e.message}"
            )
        }
    }
}

/**
 * Initialises, configures and creates a new object by receiving values into indexed slots.
 */
interface ObjectBuilder {

    companion object {
        /**
         * Create an [ObjectBuilderProvider] for the given [LocalTypeInformation.Composable].
         */
        fun makeProvider(typeInformation: LocalTypeInformation.Composable): ObjectBuilderProvider =
                makeProvider(typeInformation.typeIdentifier, typeInformation.constructor, typeInformation.properties, false)

        /**
         * Create an [ObjectBuilderProvider] for the given type, constructor and set of properties.
         *
         * The [EvolutionObjectBuilder] uses this to create [ObjectBuilderProvider]s for objects initialised via an
         * evolution constructor (i.e. a constructor annotated with [net.corda.core.serialization.DeprecatedConstructorForDeserialization]).
         */
        fun makeProvider(
                typeIdentifier: TypeIdentifier,
                constructor: LocalConstructorInformation,
                properties: Map<String, LocalPropertyInformation>,
                includeAllConstructorParameters: Boolean
        ): ObjectBuilderProvider =
                if (constructor.hasParameters) makeConstructorBasedProvider(properties, typeIdentifier, constructor, includeAllConstructorParameters)
                else makeSetterBasedProvider(properties, typeIdentifier, constructor)

        private fun makeConstructorBasedProvider(
                properties: Map<String, LocalPropertyInformation>,
                typeIdentifier: TypeIdentifier,
                constructor: LocalConstructorInformation,
                includeAllConstructorParameters: Boolean
        ): ObjectBuilderProvider {
            requireForSer(properties.values.all {
                when (it) {
                    is LocalPropertyInformation.ConstructorPairedProperty ->
                        it.constructorSlot.constructorInformation == constructor
                    is LocalPropertyInformation.PrivateConstructorPairedProperty ->
                        it.constructorSlot.constructorInformation == constructor
                    else -> true
                }
            }) { "Constructor passed in must match the constructor the properties are referring to" }
            val constructorIndices = properties.mapValues { (name, property) ->
                when (property) {
                    is LocalPropertyInformation.ConstructorPairedProperty -> property.constructorSlot.parameterIndex
                    is LocalPropertyInformation.PrivateConstructorPairedProperty -> property.constructorSlot.parameterIndex
                    is LocalPropertyInformation.CalculatedProperty -> IGNORE_COMPUTED
                    else -> throw NotSerializableException(
                            "Type ${typeIdentifier.prettyPrint(false)} has constructor arguments, " +
                                    "but property $name is not constructor-paired"
                    )
                }
            }.toMutableMap()

            if (includeAllConstructorParameters) {
                addMissingConstructorParameters(constructorIndices, constructor)
            }

            val propertySlots = constructorIndices.keys.mapIndexed { slot, name -> name to slot }.toMap()

            return ObjectBuilderProvider(propertySlots) {
                ConstructorBasedObjectBuilder(constructor, constructorIndices.values.toIntArray())
            }
        }

        private fun addMissingConstructorParameters(constructorIndices: MutableMap<String, Int>, constructor: LocalConstructorInformation) {
            // Add constructor parameters not in the list of properties
            // so we can use them in object evolution
            for ((parameterIndex, parameter) in constructor.parameters.withIndex()) {
                // Only use the parameters not already matched to properties
                constructorIndices.putIfAbsent(parameter.name, parameterIndex)
            }
        }

        private fun makeSetterBasedProvider(
                properties: Map<String, LocalPropertyInformation>,
                typeIdentifier: TypeIdentifier,
                constructor: LocalConstructorInformation
        ): ObjectBuilderProvider {
            val setters = properties.mapValues { (name, property) ->
                when (property) {
                    is LocalPropertyInformation.GetterSetterProperty -> SetterCaller(property.observedSetter)
                    is LocalPropertyInformation.CalculatedProperty -> null
                    else -> throw NotSerializableException(
                            "Type ${typeIdentifier.prettyPrint(false)} has no constructor arguments, " +
                                    "but property $name is constructor-paired"
                    )
                }
            }

            val propertySlots = setters.keys.mapIndexed { slot, name -> name to slot }.toMap()

            return ObjectBuilderProvider(propertySlots) {
                SetterBasedObjectBuilder(ConstructorCaller(constructor.observedMethod), setters.values.toList())
            }
        }
    }

    /**
     * Begin building the object.
     */
    fun initialize()

    /**
     * Populate one of the builder's slots with a value.
     */
    fun populate(slot: Int, value: Any?)

    /**
     * Return the completed, configured with the values in the builder's slots,
     */
    fun build(): Any
}

/**
 * An [ObjectBuilder] which builds an object by calling its default no-argument constructor to obtain an instance,
 * and calling a setter method for each value populated into one of its slots.
 */
private class SetterBasedObjectBuilder(
        private val constructor: ConstructorCaller,
        private val setters: List<SetterCaller?>
) : ObjectBuilder {

    private lateinit var target: Any

    override fun initialize() {
        target = constructor.invoke(emptyArray())
    }

    override fun populate(slot: Int, value: Any?) {
        setters[slot]?.invoke(target, value)
    }

    override fun build(): Any = target
}

/**
 * An [ObjectBuilder] which builds an object by collecting the values populated into its slots into a parameter array,
 * and calling a constructor with those parameters to obtain the configured object instance.
 */
private class ConstructorBasedObjectBuilder(
        private val constructorInfo: LocalConstructorInformation,
        private val slotToCtorArgIdx: IntArray
) : ObjectBuilder {

    private val constructor = ConstructorCaller(constructorInfo.observedMethod)
    private val params = arrayOfNulls<Any>(constructorInfo.parameters.size)

    init {
        requireForSer(slotToCtorArgIdx.all { it in params.indices || it == IGNORE_COMPUTED }) {
            "Argument indexes must be in ${params.indices}. Slot to arg indexes passed in are ${slotToCtorArgIdx.toList()}"
        }
    }

    override fun initialize() {}

    override fun populate(slot: Int, value: Any?) {
        val parameterIndex = slotToCtorArgIdx[slot]
        if (parameterIndex != IGNORE_COMPUTED) params[parameterIndex] = value
    }

    override fun build(): Any {
        // CORDA-3504
        // The check below would cause failures, because in some cases objects ARE instantiated with
        // parameters that are detected as mandatory but not actually set
//        requireForSer(
//                constructorInfo.parameters.zip(params)
//                        .all { (param, value) -> !param.isMandatory || value != null }
//        ) { "Some mandatory constructor parameters are not set" }
        return constructor.invoke(params)
    }
}

/**
 * An [ObjectBuilder] that wraps an underlying [ObjectBuilder], routing the property values assigned to its slots to the
 * matching slots in the underlying builder, and discarding values for which the underlying builder has no slot.
 */
class EvolutionObjectBuilder(
        private val localBuilder: ObjectBuilder,
        private val slotAssignments: IntArray,
        private val remoteProperties: List<String>,
        private val mustPreserveData: Boolean
) : ObjectBuilder {

    companion object {

        const val DISCARDED: Int = -1

        /**
         * Construct an [EvolutionObjectBuilder] for the specified type, constructor and properties, mapping the list of
         * properties defined in the remote type into the matching slots on the local type's [ObjectBuilder], and discarding
         * any for which there is no matching slot.
         */
        fun makeProvider(
                typeIdentifier: TypeIdentifier,
                constructor: LocalConstructorInformation,
                localProperties: Map<String, LocalPropertyInformation>,
                remoteTypeInformation: RemoteTypeInformation.Composable,
                mustPreserveData: Boolean
        ): () -> ObjectBuilder {
            val localBuilderProvider = ObjectBuilder.makeProvider(typeIdentifier, constructor, localProperties, true)

            val remotePropertyNames = remoteTypeInformation.properties.keys.sorted()
            val reroutedIndices = remotePropertyNames.map { propertyName ->
                localBuilderProvider.propertySlots[propertyName] ?: DISCARDED
            }.toIntArray()

            return {
                EvolutionObjectBuilder(
                        localBuilderProvider(),
                        reroutedIndices,
                        remotePropertyNames,
                        mustPreserveData
                )
            }
        }
    }

    override fun initialize() {
        localBuilder.initialize()
    }

    override fun populate(slot: Int, value: Any?) {
        val slotAssignment = slotAssignments[slot]
        if (slotAssignment == DISCARDED) {
            if (mustPreserveData && value != null) {
                throw NotSerializableException(
                        "Non-null value $value provided for property ${remoteProperties[slot]}, " +
                                "which is not supported in this version"
                )
            }
        } else {
            localBuilder.populate(slotAssignment, value)
        }
    }

    override fun build(): Any = localBuilder.build()
}

private fun requireForSer(requirement: Boolean, message: () -> String) {
    if (!requirement) throw NotSerializableException(message())
}
