package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.model.*
import java.io.NotSerializableException

/**
 * Creates a new [ObjectBuilder] ready to be populated with deserialized values so that it can create an object instance.
 *
 * @property propertySlots The slot indices of the properties written by the provided [ObjectBuilder], by property name.
 * @param provider The thunk that provides a new, empty [ObjectBuilder]
 */
data class ObjectBuilderProvider(val propertySlots: Map<String, Int>, private val provider: () -> ObjectBuilder)
    : () -> ObjectBuilder by provider

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
                         properties: Map<String, LocalPropertyInformation>): ObjectBuilderProvider {
            val nonCalculatedProperties = properties.filterNot { (_, property) -> property.isCalculated }
            val propertySlots = nonCalculatedProperties.keys.mapIndexed {
                slot, name -> name to slot
            }.toMap(LinkedHashMap())

            val constructorIndices = nonCalculatedProperties.values.mapNotNull { property ->
                when(property) {
                    is LocalPropertyInformation.ConstructorPairedProperty -> property.constructorSlot.parameterIndex
                    is LocalPropertyInformation.PrivateConstructorPairedProperty -> property.constructorSlot.parameterIndex
                    else -> null
                }
            }.toIntArray()

            if (constructorIndices.isNotEmpty()) {
                if (constructorIndices.size != nonCalculatedProperties.size) {
                    throw NotSerializableException(
                            "Some but not all properties of ${typeIdentifier.prettyPrint(false)} " +
                            "are constructor-based")
                }
                return ObjectBuilderProvider(propertySlots) {
                    ConstructorBasedObjectBuilder(constructor, constructorIndices)
                }
            }

            val getterSetter = nonCalculatedProperties.asSequence().mapNotNull { (name, property) ->
                if (property is LocalPropertyInformation.GetterSetterProperty) name to property
                else throw NotSerializableException(
                        "Property $name of type ${typeIdentifier.prettyPrint(false)} " +
                        "with default no-argument constructor is not a getter/setter property")
            }.toMap(LinkedHashMap())

            return ObjectBuilderProvider(propertySlots) {
                SetterBasedObjectBuilder(constructor, getterSetter.values.toList())
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
        private val constructor: LocalConstructorInformation,
        private val properties: List<LocalPropertyInformation.GetterSetterProperty>): ObjectBuilder {

    private lateinit var target: Any

    override fun initialize() {
        target = constructor.observedMethod.call()
    }

    override fun populate(slot: Int, value: Any?) {
        properties[slot].observedSetter.invoke(target, value)
    }

    override fun build(): Any = target
}

/**
 * An [ObjectBuilder] which builds an object by collecting the values populated into its slots into a parameter array,
 * and calling a constructor with those parameters to obtain the configured object instance.
 */
private class ConstructorBasedObjectBuilder(
        private val constructor: LocalConstructorInformation,
        private val parameterIndices: IntArray): ObjectBuilder {

    private val params = arrayOfNulls<Any>(parameterIndices.size)

    override fun initialize() {}

    override fun populate(slot: Int, value: Any?) {
        val parameterIndex = parameterIndices[slot]
        params[parameterIndex] = value
    }

    override fun build(): Any = constructor.observedMethod.call(*params)
}

/**
 * An [ObjectBuilder] that wraps an underlying [ObjectBuilder], routing the property values assigned to its slots to the
 * matching slots in the underlying builder, and discarding values for which the underlying builder has no slot.
 */
class EvolutionObjectBuilder(private val localBuilder: ObjectBuilder, private val slotAssignments: IntArray): ObjectBuilder {

    companion object {
        /**
         * Construct an [EvolutionObjectBuilder] for the specified type, constructor and properties, mapping the list of
         * properties defined in the remote type into the matching slots on the local type's [ObjectBuilder], and discarding
         * any for which there is no matching slot.
         */
        fun makeProvider(typeIdentifier: TypeIdentifier, constructor: LocalConstructorInformation, localProperties: Map<String, LocalPropertyInformation>, remoteProperties: List<String>): () -> ObjectBuilder {
            val localBuilderProvider = ObjectBuilder.makeProvider(typeIdentifier, constructor, localProperties)

            val reroutedIndices = remoteProperties.map { propertyName ->
                localBuilderProvider.propertySlots[propertyName] ?: -1
            }.toIntArray()

            return {
                EvolutionObjectBuilder(localBuilderProvider(), reroutedIndices)
            }
        }
    }

    override fun initialize() {
        localBuilder.initialize()
    }

    override fun populate(slot: Int, value: Any?) {
        val slotAssignment = slotAssignments[slot]
        if (slotAssignment != -1) localBuilder.populate(slotAssignment, value)
    }

    override fun build(): Any = localBuilder.build()
}