package net.corda.serialization.internal.model

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Obtains the value of a property from an instance of the type to which that property belongs, either by calling a getter method
 * or by reading the value of a private backing field.
 */
sealed class PropertyReader {

    companion object {
        /**
         * Make a [PropertyReader] based on the provided [LocalPropertyInformation].
         */
        fun make(propertyInformation: LocalPropertyInformation) = when(propertyInformation) {
            is LocalPropertyInformation.GetterSetterProperty -> GetterReader(propertyInformation.observedGetter)
            is LocalPropertyInformation.ConstructorPairedProperty -> GetterReader(propertyInformation.observedGetter)
            is LocalPropertyInformation.ReadOnlyProperty -> GetterReader(propertyInformation.observedGetter)
            is LocalPropertyInformation.CalculatedProperty -> GetterReader(propertyInformation.observedGetter)
            is LocalPropertyInformation.PrivateConstructorPairedProperty -> FieldReader(propertyInformation.observedField)
        }
    }

    /**
     * Get the value of the property from the supplied instance, or null if the instance is itself null.
     */
    abstract fun read(obj: Any?): Any?

    /**
     * Reads a property using a getter [Method].
     */
    class GetterReader(private val getter: Method): PropertyReader() {
        init {
            getter.isAccessible = true
        }

        override fun read(obj: Any?): Any? = if (obj == null) null else getter.invoke(obj)
    }

    /**
     * Reads a property using a backing [Field].
     */
    class FieldReader(private val field: Field): PropertyReader() {
        init {
            field.isAccessible = true
        }

        override fun read(obj: Any?): Any? = if (obj == null) null else field.get(obj)
    }
}