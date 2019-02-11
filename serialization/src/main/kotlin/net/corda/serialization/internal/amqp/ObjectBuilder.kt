package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.model.*
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
data class ObjectBuilderProvider(val propertySlots: Map<String, Int>, private val provider: () -> ObjectBuilder)
    : () -> ObjectBuilder by provider

/**
 * Wraps the operation of calling a constructor, with helpful exception handling.
 */
private class ConstructorCaller(private val javaConstructor: Constructor<Any>): (Array<Any?>) -> Any {

    override fun invoke(parameters: Array<Any?>): Any =
        try {
            javaConstructor.newInstance(*parameters)
        } catch (e: InvocationTargetException) {
            throw NotSerializableException(
                    "Constructor for ${javaConstructor.declaringClass} (isAccessible=${javaConstructor.isAccessible}) " +
                    "failed when called with parameters ${parameters.toList()}: ${e.cause!!.message}")
        } catch (e: IllegalAccessException) {
            throw NotSerializableException(
                    "Constructor for ${javaConstructor.declaringClass} (isAccessible=${javaConstructor.isAccessible}) " +
                    "not accessible: ${e.message}")
        }
}

/**
 * Wraps the operation of calling a setter, with helpful exception handling.
 */
private class SetterCaller(val setter: Method): (Any, Any?) -> Unit {
    override fun invoke(target: Any, value: Any?) {
        try {
            setter.invoke(target, value)
        } catch (e: InvocationTargetException) {
            throw NotSerializableException(
                    "Setter ${setter.declaringClass}.${setter.name} (isAccessible=${setter.isAccessible} " +
                            "failed when called with parameter $value: ${e.cause!!.message}")
        } catch (e: IllegalAccessException) {
            throw NotSerializableException(
                    "Setter ${setter.declaringClass}.${setter.name} (isAccessible=${setter.isAccessible} " +
                    "not accessible: ${e.message}")
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
                makeProvider(typeInformation.typeIdentifier, typeInformation.constructor, typeInformation.properties)

        /**
         * Create an [ObjectBuilderProvider] for the given type, constructor and set of properties.
         *
         * The [EvolutionObjectBuilder] uses this to create [ObjectBuilderProvider]s for objects initialised via an
         * evolution constructor (i.e. a constructor annotated with [DeprecatedConstructorForDeserialization]).
         */
        fun makeProvider(typeIdentifier: TypeIdentifier,
                         constructor: LocalConstructorInformation,
                         properties: Map<String, LocalPropertyInformation>): ObjectBuilderProvider =
            if (constructor.hasParameters) makeConstructorBasedProvider(properties, typeIdentifier, constructor)
            else makeGetterSetterProvider(properties, typeIdentifier, constructor)

        private fun makeConstructorBasedProvider(properties: Map<String, LocalPropertyInformation>, typeIdentifier: TypeIdentifier, constructor: LocalConstructorInformation): ObjectBuilderProvider {
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
            }

            val propertySlots = constructorIndices.keys.mapIndexed { slot, name -> name to slot }.toMap()

            return ObjectBuilderProvider(propertySlots) {
                ConstructorBasedObjectBuilder(ConstructorCaller(constructor.observedMethod), constructorIndices.values.toIntArray())
            }
        }

        private fun makeGetterSetterProvider(properties: Map<String, LocalPropertyInformation>, typeIdentifier: TypeIdentifier, constructor: LocalConstructorInformation): ObjectBuilderProvider {
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
        private val setters: List<SetterCaller?>): ObjectBuilder {

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
        private val constructor: ConstructorCaller,
        private val parameterIndices: IntArray): ObjectBuilder {

    private val params = arrayOfNulls<Any>(parameterIndices.count { it != IGNORE_COMPUTED })

    override fun initialize() {}

    override fun populate(slot: Int, value: Any?) {
        val parameterIndex = parameterIndices[slot]
        if (parameterIndex != IGNORE_COMPUTED) params[parameterIndex] = value
    }

    override fun build(): Any = constructor.invoke(params)
}

/**
 * An [ObjectBuilder] that wraps an underlying [ObjectBuilder], routing the property values assigned to its slots to the
 * matching slots in the underlying builder, and discarding values for which the underlying builder has no slot.
 */
class EvolutionObjectBuilder(private val localBuilder: ObjectBuilder,
                             private val slotAssignments: IntArray,
                             private val remoteProperties: List<String>,
                             private val mustPreserveData: Boolean): ObjectBuilder {

    companion object {

        const val DISCARDED : Int = -1

        /**
         * Construct an [EvolutionObjectBuilder] for the specified type, constructor and properties, mapping the list of
         * properties defined in the remote type into the matching slots on the local type's [ObjectBuilder], and discarding
         * any for which there is no matching slot.
         */
        fun makeProvider(typeIdentifier: TypeIdentifier,
                         constructor: LocalConstructorInformation,
                         localProperties: Map<String, LocalPropertyInformation>,
                         remoteTypeInformation: RemoteTypeInformation.Composable,
                         mustPreserveData: Boolean): () -> ObjectBuilder {
            val localBuilderProvider = ObjectBuilder.makeProvider(typeIdentifier, constructor, localProperties)

            val remotePropertyNames = remoteTypeInformation.properties.keys.sorted()
            val reroutedIndices = remotePropertyNames.map { propertyName ->
                localBuilderProvider.propertySlots[propertyName] ?: DISCARDED
            }.toIntArray()

            return {
                EvolutionObjectBuilder(
                        localBuilderProvider(),
                        reroutedIndices,
                        remotePropertyNames,
                        mustPreserveData)
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