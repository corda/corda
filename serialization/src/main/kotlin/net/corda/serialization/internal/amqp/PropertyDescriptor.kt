package net.corda.serialization.internal.amqp

import com.google.common.reflect.TypeToken
import net.corda.core.KeepForDJVM
import net.corda.core.internal.isPublic
import net.corda.serialization.internal.amqp.MethodClassifier.*
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Type

/**
 * Encapsulates the property of a class and its potential getter and setter methods.
 *
 * @property field a property of a class.
 * @property setter the method of a class that sets the field. Determined by locating
 * a function called setXyz on the class for the property named in field as xyz.
 * @property getter the method of a class that returns a fields value. Determined by
 * locating a function named getXyz for the property named in field as xyz.
 */
@KeepForDJVM
data class PropertyDescriptor(val field: Field?, val setter: Method?, val getter: Method?) {
    override fun toString() = StringBuilder("").apply {
        appendln("Property - ${field?.name ?: "null field"}\n")
        appendln("  getter - ${getter?.name ?: "no getter"}")
        appendln("  setter - ${setter?.name ?: "no setter"}")
    }.toString()

    /**
     * Check the types of the field, getter and setter methods against each other.
     */
    fun validate() {
        getter?.apply {
            val getterType = genericReturnType
            field?.apply {
                if (!TypeToken.of(getterType).isSupertypeOf(genericReturnType))
                    throw AMQPNotSerializableException(
                            declaringClass,
                            "Defined getter for parameter $name returns type $getterType " +
                                    "yet underlying type is $genericType")
            }
        }

        setter?.apply {
            val setterType = genericParameterTypes[0]!!

            field?.apply {
                if (!TypeToken.of(genericType).isSupertypeOf(setterType))
                    throw AMQPNotSerializableException(
                            declaringClass,
                            "Defined setter for parameter $name takes parameter of type $setterType " +
                                    "yet underlying type is $genericType")
            }

            getter?.apply {
                if (!TypeToken.of(genericReturnType).isSupertypeOf(setterType))
                    throw AMQPNotSerializableException(
                            declaringClass,
                            "Defined setter for parameter $name takes parameter of type $setterType, " +
                                    "but getter returns $genericReturnType")
            }
        }
    }
}

// match an uppercase letter that also has a corresponding lower case equivalent
private val propertyMethodRegex = Regex("(?<type>get|set|is)(?<var>\\p{Lu}.*)")

/**
 * Collate the properties of a class and match them with their getter and setter
 * methods as per a JavaBean.
 *
 * for a property
 *      exampleProperty
 *
 * We look for methods
 *      setExampleProperty
 *      getExampleProperty
 *      isExampleProperty
 *
 * Where getExampleProperty must return a type compatible with exampleProperty, setExampleProperty must
 * take a single parameter of a type compatible with exampleProperty and isExampleProperty must
 * return a boolean
 */
fun Class<out Any?>.propertyDescriptors(): Map<String, PropertyDescriptor> {
    val fieldProperties = superclassChain().declaredFields().byFieldName()

    return superclassChain().declaredMethods()
            .thatArePublic()
            .thatArePropertyMethods()
            .withValidSignature()
            .byNameAndClassifier(fieldProperties.keys)
            .toClassProperties(fieldProperties)
            .validated()
}

// Generate the sequence of classes starting with this class and ascending through it superclasses.
private fun Class<*>.superclassChain() = generateSequence(this, Class<*>::getSuperclass)

// Obtain the fields declared by all classes in this sequence of classes.
private fun Sequence<Class<*>>.declaredFields() = flatMap { it.declaredFields.asSequence() }

// Obtain the methods declared by all classes in this sequence of classes.
private fun Sequence<Class<*>>.declaredMethods() = flatMap { it.declaredMethods.asSequence() }

// Map a sequence of fields by field name.
private fun Sequence<Field>.byFieldName() = map { it.name to it }.toMap()

// Select only those methods that are public (and are not the "getClass" method)
private fun Sequence<Method>.thatArePublic() = filter { it.isPublic && it.name != "getClass" }

// Select only those methods that are isX/getX/setX methods
private fun Sequence<Method>.thatArePropertyMethods() = map { method ->
    propertyMethodRegex.find(method.name)?.let {
        PropertyNamedMethod(
                method,
                it.groups[2]!!.value,
                MethodClassifier.valueOf(it.groups[1]!!.value.toUpperCase()))
    }
}.filterNotNull()

// Pick only those methods whose signatures are valid, discarding the remainder without warning.
private fun Sequence<PropertyNamedMethod>.withValidSignature() = filter { it.hasValidSignature() }

// Group methods by field name, decapitalising the name to match the name of the underlying field where available
private fun Sequence<PropertyNamedMethod>.byFieldName(fieldNames: Set<String>) =
        groupBy {
            if (it.fieldName.decapitalize() in fieldNames) it.fieldName.decapitalize()
            else it.fieldName
        }

// Group methods by name and classifier, picking the method with the least generic signature if there is more than one
// of a given name and type.
private fun Sequence<PropertyNamedMethod>.byNameAndClassifier(fieldNames: Set<String>) =
        byFieldName(fieldNames).mapValues { it.value.leastGenericByClassifier() }

private fun List<PropertyNamedMethod>.leastGenericByClassifier() =
        groupBy(PropertyNamedMethod::classifier, PropertyNamedMethod::method)
                .pickLeastGeneric()

// Which of the three types of property method the method is.
private enum class MethodClassifier { GET, SET, IS }

private data class PropertyNamedMethod(val method: Method, val fieldName: String, val classifier: MethodClassifier) {
    // Validate the method's signature against its classifier
    fun hasValidSignature(): Boolean = when (classifier) {
        GET -> method.parameterCount == 0 && method.returnType != Void.TYPE
        SET -> method.parameterCount == 1 && method.returnType == Void.TYPE
        IS -> method.parameterCount == 0 &&
                (method.returnType == Boolean::class.java ||
                        method.returnType == Boolean::class.javaObjectType)
    }
}

private fun Map<MethodClassifier, Method>.setter() = this[SET]
private fun Map<MethodClassifier, Method>.getter() = this[GET] ?: this[IS]

// Construct a map of PropertyDescriptors by name, by merging the raw field map with the map of classified property methods
private fun Map<String, Map<MethodClassifier, Method>>.toClassProperties(fieldMap: Map<String, Field>): Map<String, PropertyDescriptor> =
        fieldMap.getFieldsWithoutAccessors(keys) +
                mapValues {
                    PropertyDescriptor(
                            fieldMap[it.key],
                            it.value.setter(),
                            it.value.getter()
                    )
                }

// Get only those fields in the field map for which we do not have accessor methods
private fun Map<String, Field>.getFieldsWithoutAccessors(accessorNames: Set<String>) =
        filterKeys { it !in accessorNames }
                .mapValues { PropertyDescriptor(it.value, null, null) }

// Select the least generic from a list of methods having the same field classifier
private fun Map<MethodClassifier, List<Method>>.pickLeastGeneric() = mapValues {
    when (it.key) {
        IS -> it.value.first()
        GET -> it.value.reduce(leastGenericBy { genericReturnType })
        SET -> it.value.reduce(leastGenericBy { genericParameterTypes[0] })
    }
}

// Select the least generic of two methods by a type associated with each.
private fun leastGenericBy(feature: Method.() -> Type): (Method, Method) -> Method = { left, right ->
    if (TypeToken.of(left.feature()).isSupertypeOf(right.feature())) right else left
}

// Throw an exception if any property descriptor is inconsistent, e.g. the types don't match
private fun Map<String, PropertyDescriptor>.validated() = apply {
    forEach { it.value.validate() }
}
