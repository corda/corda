package net.corda.serialization.internal.amqp

import com.google.common.reflect.TypeToken
import net.corda.core.KeepForDJVM
import net.corda.core.internal.isPublic
import net.corda.serialization.internal.amqp.MethodClassifier.*
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Type
import java.util.*

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
                if (!getterType.isSupertypeOf(genericReturnType))
                    throw AMQPNotSerializableException(
                            declaringClass,
                            "Defined getter for parameter $name returns type $getterType " +
                                    "yet underlying type is $genericType")
            }
        }

        setter?.apply {
            val setterType = genericParameterTypes[0]!!

            field?.apply {
                if (!genericType.isSupertypeOf(setterType))
                    throw AMQPNotSerializableException(
                            declaringClass,
                            "Defined setter for parameter $name takes parameter of type $setterType " +
                                    "yet underlying type is $genericType")
            }

            getter?.apply {
                if (!genericReturnType.isSupertypeOf(setterType))
                    throw AMQPNotSerializableException(
                            declaringClass,
                            "Defined setter for parameter $name takes parameter of type $setterType, " +
                                    "but getter returns $genericReturnType")
            }
        }
    }
}

private fun Type.isSupertypeOf(that: Type) = TypeToken.of(this).isSupertypeOf(that)

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
    propertyMethodRegex.find(method.name)?.let { result ->
        PropertyNamedMethod(
                result.groups[2]!!.value,
                MethodClassifier.valueOf(result.groups[1]!!.value.toUpperCase()),
                method)
    }
}.filterNotNull()

// Pick only those methods whose signatures are valid, discarding the remainder without warning.
private fun Sequence<PropertyNamedMethod>.withValidSignature() = filter { it.hasValidSignature() }

// Group methods by name and classifier, picking the method with the least generic signature if there is more than one
// of a given name and type.
private fun Sequence<PropertyNamedMethod>.byNameAndClassifier(fieldNames: Set<String>): Map<String, Map<MethodClassifier, Method>> {
    val result = mutableMapOf<String, EnumMap<MethodClassifier, Method>>()

    forEach { (fieldName, classifier, method) ->
        result.compute(getPropertyName(fieldName, fieldNames)) { _, byClassifier ->
            (byClassifier ?: EnumMap(MethodClassifier::class.java)).merge(classifier, method)
        }
    }

    return result
}

// Merge the given method into a map of methods by method classifier, picking the least generic method for each classifier.
private fun EnumMap<MethodClassifier, Method>.merge(classifier: MethodClassifier, method: Method): EnumMap<MethodClassifier, Method> {
    compute(classifier) { _, existingMethod ->
        if (existingMethod == null) method
        else when (classifier) {
            IS -> existingMethod
            GET -> leastGenericBy({ genericReturnType }, existingMethod, method)
            SET -> leastGenericBy({ genericParameterTypes[0] }, existingMethod, method)
        }
    }
    return this
}

// Make the property name conform to the underlying field name, if there is one.
private fun getPropertyName(propertyName: String, fieldNames: Set<String>) =
        if (propertyName.decapitalize() in fieldNames) propertyName.decapitalize()
        else propertyName


// Which of the three types of property method the method is.
private enum class MethodClassifier { GET, SET, IS }

private data class PropertyNamedMethod(val fieldName: String, val classifier: MethodClassifier, val method: Method) {
    // Validate the method's signature against its classifier
    fun hasValidSignature(): Boolean = method.run {
        when (classifier) {
            GET -> parameterCount == 0 && returnType != Void.TYPE
            SET -> parameterCount == 1 && returnType == Void.TYPE
            IS -> parameterCount == 0 &&
                    (returnType == Boolean::class.java ||
                            returnType == Boolean::class.javaObjectType)
        }
    }
}

// Construct a map of PropertyDescriptors by name, by merging the raw field map with the map of classified property methods
private fun Map<String, Map<MethodClassifier, Method>>.toClassProperties(fieldMap: Map<String, Field>): Map<String, PropertyDescriptor> {
    val result = mutableMapOf<String, PropertyDescriptor>()

    // Fields for which we have no property methods
    for ((name, field) in fieldMap) {
        if (name !in keys) {
            result[name] = PropertyDescriptor(field, null, null)
        }
    }

    for ((name, methodMap) in this) {
        result[name] = PropertyDescriptor(
                fieldMap[name],
                methodMap[SET],
                methodMap[GET] ?: methodMap[IS]
        )
    }

    return result
}

// Select the least generic of two methods by a type associated with each.
private fun leastGenericBy(feature: Method.() -> Type, first: Method, second: Method) =
        if (first.feature().isSupertypeOf(second.feature())) second else first

// Throw an exception if any property descriptor is inconsistent, e.g. the types don't match
private fun Map<String, PropertyDescriptor>.validated() = apply {
    forEach { _, value -> value.validate() }
}
