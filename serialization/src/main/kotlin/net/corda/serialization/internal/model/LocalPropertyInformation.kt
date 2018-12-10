package net.corda.serialization.internal.model

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Represents the information we have about a property of a type.
 */
sealed class LocalPropertyInformation(val isCalculated: Boolean) {

    /**
     * [LocalTypeInformation] for the type of the property.
     */
    abstract val type: LocalTypeInformation

    /**
     * True if the property is a primitive type or is flagged as non-nullable, false otherwise.
     */
    abstract val isMandatory: Boolean

    /**
     * A property of an interface, for which we have only a getter method.
     *
     * @param observedGetter The method which can be used to obtain the value of this property from an instance of its owning type.
     */
    data class ReadOnlyProperty(val observedGetter: Method, override val type: LocalTypeInformation, override val isMandatory: Boolean) : LocalPropertyInformation(false)

    /**
     * A property for which we have both a getter, and a matching slot in an array of constructor parameters.
     *
     * @param observedGetter The method which can be used to obtain the value of this property from an instance of its owning type.
     * @param constructorSlot The [ConstructorSlot] to which the property corresponds, used to populate an array of
     * constructor arguments when creating instances of its owning type.
     */
    data class ConstructorPairedProperty(val observedGetter: Method, val constructorSlot: ConstructorSlot, override val type: LocalTypeInformation, override val isMandatory: Boolean) : LocalPropertyInformation(false)

    /**
     * A property for which we have no getter, but for which there is a backing field a matching slot in an array of
     * constructor parameters.
     *
     * @param observedField The field which can be used to obtain the value of this property from an instance of its owning type.
     * @param constructorSlot The [ConstructorSlot] to which the property corresponds, used to populate an array of
     * constructor arguments when creating instances of its owning type.
     */
    data class PrivateConstructorPairedProperty(val observedField: Field, val constructorSlot: ConstructorSlot, override val type: LocalTypeInformation, override val isMandatory: Boolean) : LocalPropertyInformation(false)

    /**
     * A property for which we have both getter and setter methods (usually belonging to a POJO which is initialised
     * with the default no-argument constructor and then configured via setters).
     *
     * @param observedGetter The method which can be used to obtain the value of this property from an instance of its owning type.
     * @param observedSetter The method which can be used to set the value of this property on an instance of its owning type.
     */
    data class GetterSetterProperty(val observedGetter: Method, val observedSetter: Method, override val type: LocalTypeInformation, override val isMandatory: Boolean) : LocalPropertyInformation(false)

    /**
     * A property for which we have only a getter method, which is annotated with [SerializableCalculatedProperty].
     */
    data class CalculatedProperty(val observedGetter: Method, override val type: LocalTypeInformation, override val isMandatory: Boolean) : LocalPropertyInformation(true)
}


/**
 * References a slot in an array of constructor parameters.
 */
data class ConstructorSlot(val parameterIndex: Int, val constructorInformation: LocalConstructorInformation) {
    val parameterInformation get() = constructorInformation.parameters.getOrNull(parameterIndex) ?:
    throw IllegalStateException("Constructor slot refers to parameter #$parameterIndex " +
            "of constructor $constructorInformation, " +
            "but constructor has only ${constructorInformation.parameters.size} parameters")
}