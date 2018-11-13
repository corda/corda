package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.model.*
import java.io.NotSerializableException

interface ObjectBuilder {

    companion object {
        fun makeProvider(typeInformation: LocalTypeInformation.Composable): () -> ObjectBuilder =
                makeProvider(typeInformation.typeIdentifier, typeInformation.constructor, typeInformation.properties)

        fun makeProvider(typeIdentifier: TypeIdentifier, constructor: LocalConstructorInformation, properties: Map<String, LocalPropertyInformation>): () -> ObjectBuilder {
            val nonCalculatedProperties = properties.asSequence()
                    .filterNot { (name, property) -> property.isCalculated }
                    .sortedBy { (name, _) -> name }
                    .map { (_, property) -> property }
                    .toList()

            val propertyIndices = nonCalculatedProperties.mapNotNull {
                when(it) {
                    is LocalPropertyInformation.ConstructorPairedProperty -> it.constructorSlot.parameterIndex
                    is LocalPropertyInformation.PrivateConstructorPairedProperty -> it.constructorSlot.parameterIndex
                    else -> null
                }
            }.toIntArray()

            if (propertyIndices.isNotEmpty()) {
                if (propertyIndices.size != nonCalculatedProperties.size) {
                    throw NotSerializableException(
                            "Some but not all properties of ${typeIdentifier.prettyPrint(false)} " +
                                    "are constructor-based")
                }
                return { ConstructorBasedObjectBuilder(constructor, propertyIndices) }
            }

            val getterSetter = nonCalculatedProperties.filterIsInstance<LocalPropertyInformation.GetterSetterProperty>()
            return { SetterBasedObjectBuilder(constructor, getterSetter) }
        }
    }

    fun initialize()
    fun populate(slot: Int, value: Any?)
    fun build(): Any
}

class SetterBasedObjectBuilder(
        val constructor: LocalConstructorInformation,
        val properties: List<LocalPropertyInformation.GetterSetterProperty>): ObjectBuilder {

    private lateinit var target: Any

    override fun initialize() {
        target = constructor.observedMethod.call()
    }

    override fun populate(slot: Int, value: Any?) {
        properties[slot].observedSetter.invoke(target, value)
    }

    override fun build(): Any = target
}

class ConstructorBasedObjectBuilder(
        val constructor: LocalConstructorInformation,
        val parameterIndices: IntArray): ObjectBuilder {

    private val params = arrayOfNulls<Any>(parameterIndices.size)

    override fun initialize() {}

    override fun populate(slot: Int, value: Any?) {
        if (slot >= parameterIndices.size) {
            assert(false)
        }
        val parameterIndex = parameterIndices[slot]
        if (parameterIndex >= params.size) {
            assert(false)
        }
        params[parameterIndex] = value
    }

    override fun build(): Any = constructor.observedMethod.call(*params)
}

class EvolutionObjectBuilder(private val localBuilder: ObjectBuilder, val slotAssignments: IntArray): ObjectBuilder {

    companion object {
        fun makeProvider(typeIdentifier: TypeIdentifier, constructor: LocalConstructorInformation, localProperties: Map<String, LocalPropertyInformation>, providedProperties: List<String>): () -> ObjectBuilder {
            val localBuilderProvider = ObjectBuilder.makeProvider(typeIdentifier, constructor, localProperties)
            val localPropertyIndices = localProperties.asSequence()
                    .filter { (_, property) -> !property.isCalculated }
                    .mapIndexed { slot, (name, _) -> name to slot }
                    .toMap()

            val reroutedIndices = providedProperties.map { propertyName -> localPropertyIndices[propertyName] ?: -1 }
                    .toIntArray()

            return { EvolutionObjectBuilder(localBuilderProvider(), reroutedIndices) }
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