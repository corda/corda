/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.serialization.internal.amqp

import com.google.common.primitives.Primitives
import com.google.common.reflect.TypeToken
import net.corda.core.internal.isConcreteClass
import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.ConstructorForDeserialization
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationContext
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.*
import java.lang.reflect.Field
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaType

/**
 * Code for finding the constructor we will use for deserialization.
 *
 * If any constructor is uniquely annotated with [@ConstructorForDeserialization], then that constructor is chosen.
 * An error is reported if more than one constructor is annotated.
 *
 * Otherwise, if there is a Kotlin primary constructor, it selects that, and if not it selects either the unique
 * constructor or, if there are two and one is the default no-argument constructor, the non-default constructor.
 */
fun constructorForDeserialization(type: Type): KFunction<Any> {
    val clazz = type.asClass().apply {
        if (!isConcreteClass) throw AMQPNotSerializableException(type,
                "Cannot find deserialisation constructor for non-concrete class $this")
    }

    val kotlinCtors = clazz.kotlin.constructors

    val annotatedCtors = kotlinCtors.filter { it.findAnnotation<ConstructorForDeserialization>() != null }
    if (annotatedCtors.size > 1) throw AMQPNotSerializableException(
            type,
            "More than one constructor for $clazz is annotated with @ConstructorForDeserialization.")

    val defaultCtor = kotlinCtors.firstOrNull { it.parameters.isEmpty() }
    val nonDefaultCtors = kotlinCtors.filter { it != defaultCtor }

    val preferredCandidate = annotatedCtors.firstOrNull() ?:
            clazz.kotlin.primaryConstructor ?:
            when(nonDefaultCtors.size) {
                1 -> nonDefaultCtors.first()
                0 -> defaultCtor ?: throw AMQPNotSerializableException(type, "No constructor found for $clazz.")
                else -> throw AMQPNotSerializableException(type, "No unique non-default constructor found for $clazz.")
            }

    return preferredCandidate.apply { isAccessible = true }
}

/**
 * Identifies the properties to be used during serialization by attempting to find those that match the parameters
 * to the deserialization constructor, if the class is concrete.  If it is abstract, or an interface, then use all
 * the properties.
 *
 * Note, you will need any Java classes to be compiled with the `-parameters` option to ensure constructor parameters
 * have names accessible via reflection.
 */
fun <T : Any> propertiesForSerialization(
        kotlinConstructor: KFunction<T>?,
        type: Type,
        factory: SerializerFactory): PropertySerializers = PropertySerializers.make(
            if (kotlinConstructor != null) {
                propertiesForSerializationFromConstructor(kotlinConstructor, type, factory)
            } else {
                propertiesForSerializationFromAbstract(type.asClass(), type, factory)
            }.sortedWith(PropertyAccessor)
    )

/**
 * From a constructor, determine which properties of a class are to be serialized.
 *
 * @param kotlinConstructor The constructor to be used to instantiate instances of the class
 * @param type The class's [Type]
 * @param factory The factory generating the serializer wrapping this function.
 */
internal fun <T : Any> propertiesForSerializationFromConstructor(
        kotlinConstructor: KFunction<T>,
        type: Type,
        factory: SerializerFactory): List<PropertyAccessor> {
    val clazz = (kotlinConstructor.returnType.classifier as KClass<*>).javaObjectType

    val classProperties = clazz.propertyDescriptors()

    // Annoyingly there isn't a better way to ascertain that the constructor for the class
    // has a synthetic parameter inserted to capture the reference to the outer class. You'd
    // think you could inspect the parameter and check the isSynthetic flag but that is always
    // false so given the naming convention is specified by the standard we can just check for
    // this
    kotlinConstructor.javaConstructor?.apply {
        if (parameterCount > 0 && parameters[0].name == "this$0") throw SyntheticParameterException(type)
    }

    if (classProperties.isNotEmpty() && kotlinConstructor.parameters.isEmpty()) {
        return propertiesForSerializationFromSetters(classProperties, type, factory)
    }

    return kotlinConstructor.parameters.withIndex().map { param ->
        toPropertyAccessorConstructor(param.index, param.value, classProperties, type, clazz, factory)
    }
}

private fun toPropertyAccessorConstructor(index: Int, param: KParameter, classProperties: Map<String, PropertyDescriptor>, type: Type, clazz: Class<out Any>, factory: SerializerFactory): PropertyAccessorConstructor {
    // name cannot be null, if it is then this is a synthetic field and we will have bailed
    // out prior to this
    val name = param.name!!

    // We will already have disambiguated getA for property A or a but we still need to cope
    // with the case we don't know the case of A when the parameter doesn't match a property
    // but has a getter
    val matchingProperty = classProperties[name] ?: classProperties[name.capitalize()]
    ?: throw AMQPNotSerializableException(type,
            "Constructor parameter - \"$name\" -  doesn't refer to a property of \"$clazz\"")

    // If the property has a getter we'll use that to retrieve it's value from the instance, if it doesn't
    // *for *now* we switch to a reflection based method
    val propertyReader = matchingProperty.getter?.let { getter ->
        getPublicPropertyReader(getter, type, param, name, clazz)
    } ?: matchingProperty.field?.let { field ->
        getPrivatePropertyReader(field, type)
    } ?: throw AMQPNotSerializableException(type,
            "No property matching constructor parameter named - \"$name\" - " +
            "of \"${param}\". If using Java, check that you have the -parameters option specified " +
            "in the Java compiler. Alternately, provide a proxy serializer " +
            "(SerializationCustomSerializer) if recompiling isn't an option")

    return PropertyAccessorConstructor(
            index,
            PropertySerializer.make(name, propertyReader.first, propertyReader.second, factory))
}

/**
 * If we determine a class has a constructor that takes no parameters then check for pairs of getters / setters
 * and use those
 */
fun propertiesForSerializationFromSetters(
        properties: Map<String, PropertyDescriptor>,
        type: Type,
        factory: SerializerFactory): List<PropertyAccessor> =
        properties.asSequence().withIndex().map { (index, entry) ->
            val (name, property) = entry

            val getter = property.getter
            val setter = property.setter

            if (getter == null || setter == null) return@map null

            PropertyAccessorGetterSetter(
                    index,
                    PropertySerializer.make(
                            name,
                            PublicPropertyReader(getter),
                            resolveTypeVariables(getter.genericReturnType, type),
                            factory),
                    setter)
        }.filterNotNull().toList()

private fun getPrivatePropertyReader(field: Field, type: Type) =
        PrivatePropertyReader(field, type) to resolveTypeVariables(field.genericType, type)

private fun getPublicPropertyReader(getter: Method, type: Type, param: KParameter, name: String, clazz: Class<out Any>): Pair<PublicPropertyReader, Type> {
    val returnType = resolveTypeVariables(getter.genericReturnType, type)
    val paramToken = TypeToken.of(param.type.javaType)
    val rawParamType = TypeToken.of(paramToken.rawType)

    if (!(paramToken.isSupertypeOf(returnType)
                    || paramToken.isSupertypeOf(getter.genericReturnType)
                    // cope with the case where the constructor parameter is a generic type (T etc) but we
                    // can discover it's raw type. When bounded this wil be the bounding type, unbounded
                    // generics this will be object
                    || rawParamType.isSupertypeOf(returnType)
                    || rawParamType.isSupertypeOf(getter.genericReturnType))) {
        throw AMQPNotSerializableException(
                type,
                "Property - \"$name\" - has type \"$returnType\" on \"$clazz\" " +
                        "but differs from constructor parameter type \"${param.type.javaType}\"")
    }

    return PublicPropertyReader(getter) to returnType
}

private fun propertiesForSerializationFromAbstract(
        clazz: Class<*>,
        type: Type,
        factory: SerializerFactory): List<PropertyAccessor> =
        clazz.propertyDescriptors().asSequence().withIndex().map { (index, entry) ->
            val (name, property) = entry
            if (property.getter == null || property.field == null) return@map null

            val getter = property.getter
            val returnType = resolveTypeVariables(getter.genericReturnType, type)

            PropertyAccessorConstructor(
                    index,
                    PropertySerializer.make(name, PublicPropertyReader(getter), returnType, factory))
        }.filterNotNull().toList()

internal fun interfacesForSerialization(type: Type, serializerFactory: SerializerFactory): List<Type> =
        exploreType(type, serializerFactory).toList()

private fun exploreType(type: Type, serializerFactory: SerializerFactory, interfaces: MutableSet<Type> = LinkedHashSet()): MutableSet<Type> {
    val clazz = type.asClass()

    if (clazz.isInterface) {
        // Ignore classes we've already seen, and stop exploring once we reach a branch that has no `CordaSerializable`
        // annotation or whitelisting.
        if (clazz in interfaces || serializerFactory.whitelist.isNotWhitelisted(clazz)) return interfaces
        else interfaces += type
    }

    (clazz.genericInterfaces.asSequence() + clazz.genericSuperclass)
            .filterNotNull()
            .forEach { exploreType(resolveTypeVariables(it, type), serializerFactory, interfaces) }

    return interfaces
}

/**
 * Extension helper for writing described objects.
 */
fun Data.withDescribed(descriptor: Descriptor, block: Data.() -> Unit) {
    // Write described
    putDescribed()
    enter()
    // Write descriptor
    putObject(descriptor.code ?: descriptor.name)
    block()
    exit() // exit described
}

/**
 * Extension helper for writing lists.
 */
fun Data.withList(block: Data.() -> Unit) {
    // Write list
    putList()
    enter()
    block()
    exit() // exit list
}

/**
 * Extension helper for outputting reference to already observed object
 */
fun Data.writeReferencedObject(refObject: ReferencedObject) {
    // Write described
    putDescribed()
    enter()
    // Write descriptor
    putObject(refObject.descriptor)
    putUnsignedInteger(refObject.described)
    exit() // exit described
}

fun resolveTypeVariables(actualType: Type, contextType: Type?): Type {
    val resolvedType = if (contextType != null) TypeToken.of(contextType).resolveType(actualType).type else actualType
    // TODO: surely we check it is concrete at this point with no TypeVariables
    return if (resolvedType is TypeVariable<*>) {
        val bounds = resolvedType.bounds
        return if (bounds.isEmpty()) {
            SerializerFactory.AnyType
        } else if (bounds.size == 1) {
            resolveTypeVariables(bounds[0], contextType)
        } else throw AMQPNotSerializableException(
                actualType,
                "Got bounded type $actualType but only support single bound.")
    } else {
        resolvedType
    }
}

internal fun Type.asClass(): Class<*> {
    return when(this) {
        is Class<*> -> this
        is ParameterizedType -> this.rawType.asClass()
        is GenericArrayType -> this.genericComponentType.asClass().arrayClass()
        is TypeVariable<*> -> this.bounds.first().asClass()
        is WildcardType -> this.upperBounds.first().asClass()
        // Per https://docs.oracle.com/javase/8/docs/api/java/lang/reflect/Type.html,
        // there is nothing else that it can be, so this can never happen.
        else -> throw UnsupportedOperationException("Cannot convert $this to class")
    }
}

internal fun Type.asArray(): Type? {
    return when(this) {
        is Class<*> -> this.arrayClass()
        is ParameterizedType -> DeserializedGenericArrayType(this)
        else -> null
    }
}

internal fun Class<*>.arrayClass(): Class<*> = java.lang.reflect.Array.newInstance(this, 0).javaClass

internal fun Type.isArray(): Boolean = (this is Class<*> && this.isArray) || (this is GenericArrayType)

internal fun Type.componentType(): Type {
    check(this.isArray()) { "$this is not an array type." }
    return (this as? Class<*>)?.componentType ?: (this as GenericArrayType).genericComponentType
}

internal fun Class<*>.asParameterizedType(): ParameterizedType {
    return DeserializedParameterizedType(this, this.typeParameters)
}

internal fun Type.asParameterizedType(): ParameterizedType {
    return when (this) {
        is Class<*> -> this.asParameterizedType()
        is ParameterizedType -> this
        else -> throw AMQPNotSerializableException(this, "Don't know how to convert to ParameterizedType")
    }
}

internal fun Type.isSubClassOf(type: Type): Boolean {
    return TypeToken.of(this).isSubtypeOf(TypeToken.of(type).rawType)
}

// ByteArrays, primitives and boxed primitives are not stored in the object history
internal fun suitableForObjectReference(type: Type): Boolean {
    val clazz = type.asClass()
    return type != ByteArray::class.java && (!clazz.isPrimitive && !Primitives.unwrap(clazz).isPrimitive)
}

/**
 * Common properties that are to be used in the [SerializationContext.properties] to alter serialization behavior/content
 */
internal enum class CommonPropertyNames {
    IncludeInternalInfo,
}



fun ClassWhitelist.requireWhitelisted(type: Type) {
    if (!this.isWhitelisted(type.asClass())) {
        throw AMQPNotSerializableException(
                type,
                "Class \"$type\" is not on the whitelist or annotated with @CordaSerializable.")
    }
}

fun ClassWhitelist.isWhitelisted(clazz: Class<*>) = hasListed(clazz) || hasCordaSerializable(clazz)
fun ClassWhitelist.isNotWhitelisted(clazz: Class<*>) = !this.isWhitelisted(clazz)

/**
 * Check the given [Class] has the [CordaSerializable] annotation, either directly or inherited from any of its super
 * classes or interfaces.
 */
fun hasCordaSerializable(type: Class<*>): Boolean {
    return type.isAnnotationPresent(CordaSerializable::class.java)
            || type.interfaces.any(::hasCordaSerializable)
            || (type.superclass != null && hasCordaSerializable(type.superclass))
}

fun isJavaPrimitive(type: Class<*>) = type in JavaPrimitiveTypes.primativeTypes

private object JavaPrimitiveTypes {
    val primativeTypes = hashSetOf<Class<*>>(
    Boolean::class.java,
    Char::class.java,
    Byte::class.java,
    Short::class.java,
    Int::class.java,
    Long::class.java,
    Float::class.java,
    Double::class.java,
    Void::class.java)
}
