package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.model.LocalConstructorInformation
import net.corda.serialization.internal.model.LocalPropertyInformation
import net.corda.serialization.internal.model.LocalTypeInformation
import java.io.NotSerializableException

interface ObjectBuilder {

    companion object {
        fun makeProvider(typeInformation: LocalTypeInformation.Composable): () -> ObjectBuilder {
            val nonCalculatedProperties = typeInformation.properties.asSequence()
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
                            "Some but not all properties of ${typeInformation.typeIdentifier.prettyPrint(false)} " +
                                    "are constructor-based")
                }
                return { ConstructorBasedObjectBuilder(typeInformation.constructor, propertyIndices) }
            }

            val getterSetter = nonCalculatedProperties.filterIsInstance<LocalPropertyInformation.GetterSetterProperty>()
            return { SetterBasedObjectBuilder(typeInformation.constructor, getterSetter) }
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
        val propertyIndices: IntArray): ObjectBuilder {

    private val params = arrayOfNulls<Any>(propertyIndices.size)

    override fun initialize() {}

    override fun populate(slot: Int, value: Any?) {
        params[propertyIndices[slot]] = value
    }

    override fun build(): Any = constructor.observedMethod.call(*params)
}