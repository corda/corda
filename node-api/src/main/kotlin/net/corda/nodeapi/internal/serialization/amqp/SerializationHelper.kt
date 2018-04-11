package net.corda.nodeapi.internal.serialization.amqp

import com.google.common.primitives.Primitives
import com.google.common.reflect.TypeToken
import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.ConstructorForDeserialization
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationContext
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
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
 * If there's only one constructor, it selects that.  If there are two and one is the default, it selects the other.
 * Otherwise it starts with the primary constructor in kotlin, if there is one, and then will override this with any that is
 * annotated with [@CordaConstructor].  It will report an error if more than one constructor is annotated.
 */
internal fun constructorForDeserialization(type: Type): KFunction<Any>? {
    val clazz: Class<*> = type.asClass()!!
    if (isConcrete(clazz)) {
        var preferredCandidate: KFunction<Any>? = clazz.kotlin.primaryConstructor
        var annotatedCount = 0
        val kotlinConstructors = clazz.kotlin.constructors
        val hasDefault = kotlinConstructors.any { it.parameters.isEmpty() }

        for (kotlinConstructor in kotlinConstructors) {
            if (preferredCandidate == null && kotlinConstructors.size == 1) {
                preferredCandidate = kotlinConstructor
            } else if (preferredCandidate == null && kotlinConstructors.size == 2 && hasDefault && kotlinConstructor.parameters.isNotEmpty()) {
                preferredCandidate = kotlinConstructor
            } else if (kotlinConstructor.findAnnotation<ConstructorForDeserialization>() != null) {
                if (annotatedCount++ > 0) {
                    throw NotSerializableException("More than one constructor for $clazz is annotated with @CordaConstructor.")
                }
                preferredCandidate = kotlinConstructor
            }
        }

        return preferredCandidate?.apply { isAccessible = true }
                ?: throw NotSerializableException("No constructor for deserialization found for $clazz.")
    } else {
        return null
    }
}

/**
 * Identifies the properties to be used during serialization by attempting to find those that match the parameters
 * to the deserialization constructor, if the class is concrete.  If it is abstract, or an interface, then use all
 * the properties.
 *
 * Note, you will need any Java classes to be compiled with the `-parameters` option to ensure constructor parameters
 * have names accessible via reflection.
 */
internal fun <T : Any> propertiesForSerialization(
        kotlinConstructor: KFunction<T>?,
        type: Type,
        factory: SerializerFactory) = PropertySerializers.make(
        if (kotlinConstructor != null) {
            propertiesForSerializationFromConstructor(kotlinConstructor, type, factory)
        } else {
            propertiesForSerializationFromAbstract(type.asClass()!!, type, factory)
        }.sortedWith(PropertyAccessor))


fun isConcrete(clazz: Class<*>): Boolean = !(clazz.isInterface || Modifier.isAbstract(clazz.modifiers))

/**
 * Encapsulates the property of a class and its potential getter and setter methods.
 *
 * @property field a property of a class.
 * @property setter the method of a class that sets the field. Determined by locating
 * a function called setXyz on the class for the property named in field as xyz.
 * @property getter the method of a class that returns a fields value. Determined by
 * locating a function named getXyz for the property named in field as xyz.
 */
data class PropertyDescriptor(var field: Field?, var setter: Method?, var getter: Method?, var iser: Method?) {
    override fun toString() = StringBuilder("").apply {
        appendln("Property - ${field?.name ?: "null field"}\n")
        appendln("  getter - ${getter?.name ?: "no getter"}")
        appendln("  setter - ${setter?.name ?: "no setter"}")
        appendln("  iser   - ${iser?.name ?: "no isXYZ defined"}")
    }.toString()

    constructor() : this(null, null, null, null)

    fun preferredGetter() : Method? = getter ?: iser
}

object PropertyDescriptorsRegex {
    // match an uppercase letter that also has a corresponding lower case equivalent
    val re = Regex("(?<type>get|set|is)(?<var>\\p{Lu}.*)")
}

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
 * Where setExampleProperty must return a type compatible with exampleProperty, getExampleProperty must
 * take a single parameter of a type compatible with exampleProperty and isExampleProperty must
 * return a boolean
 */
fun Class<out Any?>.propertyDescriptors(): Map<String, PropertyDescriptor> {
    val classProperties = mutableMapOf<String, PropertyDescriptor>()

    var clazz: Class<out Any?>? = this

    do {
        clazz!!.declaredFields.forEach { property ->
            classProperties.computeIfAbsent(property.name) {
                PropertyDescriptor()
            }.apply {
                this.field = property
            }
        }
        clazz = clazz.superclass
    } while (clazz != null)

    //
    // Running as two loops rather than one as we need to ensure we have captured all of the properties
    // before looking for interacting methods and need to cope with the class hierarchy introducing
    // new  properties / methods
    //
    clazz = this
    do {
        // Note: It is possible for a class to have multiple instances of a function where the types
        // differ. For example:
        //      interface I<out T> { val a: T }
        //      class D(override val a: String) : I<String>
        // instances of D will have both
        //      getA - returning a String (java.lang.String) and
        //      getA - returning an Object (java.lang.Object)
        // In this instance we take the most derived object
        //
        // In addition, only getters that take zero parameters and setters that take a single
        // parameter will be considered
        clazz!!.declaredMethods?.map { func ->
            if (!Modifier.isPublic(func.modifiers)) return@map
            if (func.name == "getClass") return@map

            PropertyDescriptorsRegex.re.find(func.name)?.apply {
                // matching means we have an func getX where the property could be x or X
                // so having pre-loaded all of the properties we try to match to either case. If that
                // fails the getter doesn't refer to a property directly, but may refer to a constructor
                // parameter that shadows a property
                val properties =
                        classProperties[groups[2]!!.value] ?:
                        classProperties[groups[2]!!.value.decapitalize()] ?:
                        // take into account those constructor properties that don't directly map to a named
                        // property which are, by default, already added to the map
                        classProperties.computeIfAbsent(groups[2]!!.value) { PropertyDescriptor() }

                properties.apply {
                    when (groups[1]!!.value) {
                        "set" -> {
                            if (func.parameterCount == 1) {
                                if (setter == null) setter = func
                                else if (TypeToken.of(setter!!.genericReturnType).isSupertypeOf(func.genericReturnType)) {
                                    setter = func
                                }
                            }
                        }
                        "get" -> {
                            if (func.parameterCount == 0) {
                                if (getter == null) getter = func
                                else if (TypeToken.of(getter!!.genericReturnType).isSupertypeOf(func.genericReturnType)) {
                                    getter = func
                                }
                            }
                        }
                        "is" -> {
                            if (func.parameterCount == 0) {
                                val rtnType = TypeToken.of(func.genericReturnType)
                                if ((rtnType == TypeToken.of(Boolean::class.java))
                                        || (rtnType == TypeToken.of(Boolean::class.javaObjectType))) {
                                    if (iser == null) iser = func
                                }
                            }
                        }
                    }
                }
            }
        }
        clazz = clazz.superclass
    } while (clazz != null)

    return classProperties
}

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
    if (kotlinConstructor.javaConstructor?.parameterCount ?: 0 > 0 &&
            kotlinConstructor.javaConstructor?.parameters?.get(0)?.name == "this$0"
    ) {
        throw SyntheticParameterException(type)
    }

    if (classProperties.isNotEmpty() && kotlinConstructor.parameters.isEmpty()) {
        return propertiesForSerializationFromSetters(classProperties, type, factory)
    }

    return mutableListOf<PropertyAccessor>().apply {
        kotlinConstructor.parameters.withIndex().forEach { param ->
            // name cannot be null, if it is then this is a synthetic field and we will have bailed
            // out prior to this
            val name = param.value.name!!

            // We will already have disambiguated getA for property A or a but we still need to cope
            // with the case we don't know the case of A when the parameter doesn't match a property
            // but has a getter
            val matchingProperty = classProperties[name] ?: classProperties[name.capitalize()] ?:
                    throw NotSerializableException(
                            "Constructor parameter - \"$name\" -  doesn't refer to a property of \"$clazz\"")

            // If the property has a getter we'll use that to retrieve it's value from the instance, if it doesn't
            // *for *know* we switch to a reflection based method
            val propertyReader = if (matchingProperty.getter != null) {
                val getter = matchingProperty.getter ?: throw NotSerializableException(
                        "Property has no getter method for - \"$name\" - of \"$clazz\". If using Java and the parameter name"
                                + "looks anonymous, check that you have the -parameters option specified in the "
                                + "Java compiler. Alternately, provide a proxy serializer "
                                + "(SerializationCustomSerializer) if recompiling isn't an option.")

                val returnType = resolveTypeVariables(getter.genericReturnType, type)
                if (!constructorParamTakesReturnTypeOfGetter(returnType, getter.genericReturnType, param.value)) {
                    throw NotSerializableException(
                            "Property - \"$name\" - has type \"$returnType\" on \"$clazz\" but differs from constructor " +
                                    "parameter type \"${param.value.type.javaType}\"")
                }

                Pair(PublicPropertyReader(getter), returnType)
            } else {
                val field = classProperties[name]!!.field ?:
                        throw NotSerializableException("No property matching constructor parameter named - \"$name\" - " +
                                "of \"$clazz\". If using Java, check that you have the -parameters option specified " +
                                "in the Java compiler. Alternately, provide a proxy serializer " +
                                "(SerializationCustomSerializer) if recompiling isn't an option")

                Pair(PrivatePropertyReader(field, type), field.genericType)
            }

            this += PropertyAccessorConstructor(
                    param.index,
                    PropertySerializer.make(name, propertyReader.first, propertyReader.second, factory))
        }
    }
}

/**
 * If we determine a class has a constructor that takes no parameters then check for pairs of getters / setters
 * and use those
 */
fun propertiesForSerializationFromSetters(
        properties: Map<String, PropertyDescriptor>,
        type: Type,
        factory: SerializerFactory): List<PropertyAccessor> {
    return mutableListOf<PropertyAccessorGetterSetter>().apply {
        var idx = 0

        properties.forEach { property ->
            val getter: Method? = property.value.preferredGetter()
            val setter: Method? = property.value.setter

            if (getter == null || setter == null) return@forEach

            if (setter.parameterCount != 1) {
                throw NotSerializableException("Defined setter for parameter ${property.value.field?.name} " +
                        "takes too many arguments")
            }

            //val setterType = setter.parameterTypes[0]!!
            val setterType = setter.genericParameterTypes[0]!!

            if ((property.value.field != null) &&
                    (!(TypeToken.of(property.value.field?.genericType!!).isSupertypeOf(setterType)))) {
                throw NotSerializableException("Defined setter for parameter ${property.value.field?.name} " +
                        "takes parameter of type $setterType yet underlying type is " +
                        "${property.value.field?.genericType!!}")
            }

            // make sure the setter returns the same type (within inheritance bounds) the getter accepts
            if (!(TypeToken.of (getter.genericReturnType).isSupertypeOf(setterType))) {
                throw NotSerializableException("Defined setter for parameter ${property.value.field?.name} " +
                        "takes parameter of type $setterType yet the defined getter returns a value of type " +
                        "${getter.returnType} [${getter.genericReturnType}]")
            }
            this += PropertyAccessorGetterSetter(
                    idx++,
                    PropertySerializer.make(property.key, PublicPropertyReader(getter),
                            resolveTypeVariables(getter.genericReturnType, type), factory),
                    setter)
        }
    }
}

private fun constructorParamTakesReturnTypeOfGetter(
        getterReturnType: Type,
        rawGetterReturnType: Type,
        param: KParameter): Boolean {
    val paramToken = TypeToken.of(param.type.javaType)
    val rawParamType = TypeToken.of(paramToken.rawType)

    return paramToken.isSupertypeOf(getterReturnType)
            || paramToken.isSupertypeOf(rawGetterReturnType)
            // cope with the case where the constructor parameter is a generic type (T etc) but we
            // can discover it's raw type. When bounded this wil be the bounding type, unbounded
            // generics this will be object
            || rawParamType.isSupertypeOf(getterReturnType)
            || rawParamType.isSupertypeOf(rawGetterReturnType)
}

private fun propertiesForSerializationFromAbstract(
        clazz: Class<*>,
        type: Type,
        factory: SerializerFactory): List<PropertyAccessor> {
    val properties = clazz.propertyDescriptors()

    return mutableListOf<PropertyAccessorConstructor>().apply {
        properties.toList().withIndex().forEach {
            val getter = it.value.second.getter ?: return@forEach
            if (it.value.second.field == null) return@forEach

            val returnType = resolveTypeVariables(getter.genericReturnType, type)
            this += PropertyAccessorConstructor(
                    it.index,
                    PropertySerializer.make(it.value.first, PublicPropertyReader(getter), returnType, factory))
        }
    }
}

internal fun interfacesForSerialization(type: Type, serializerFactory: SerializerFactory): List<Type> {
    val interfaces = LinkedHashSet<Type>()
    exploreType(type, interfaces, serializerFactory)
    return interfaces.toList()
}

private fun exploreType(type: Type?, interfaces: MutableSet<Type>, serializerFactory: SerializerFactory) {
    val clazz = type?.asClass()
    if (clazz != null) {
        if (clazz.isInterface) {
            if (serializerFactory.whitelist.isNotWhitelisted(clazz)) return // We stop exploring once we reach a branch that has no `CordaSerializable` annotation or whitelisting.
            else interfaces += type
        }
        for (newInterface in clazz.genericInterfaces) {
            if (newInterface !in interfaces) {
                exploreType(resolveTypeVariables(newInterface, type), interfaces, serializerFactory)
            }
        }
        val superClass = clazz.genericSuperclass ?: return
        exploreType(resolveTypeVariables(superClass, type), interfaces, serializerFactory)
    }
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

private fun resolveTypeVariables(actualType: Type, contextType: Type?): Type {
    val resolvedType = if (contextType != null) TypeToken.of(contextType).resolveType(actualType).type else actualType
    // TODO: surely we check it is concrete at this point with no TypeVariables
    return if (resolvedType is TypeVariable<*>) {
        val bounds = resolvedType.bounds
        return if (bounds.isEmpty()) {
            SerializerFactory.AnyType
        } else if (bounds.size == 1) {
            resolveTypeVariables(bounds[0], contextType)
        } else throw NotSerializableException("Got bounded type $actualType but only support single bound.")
    } else {
        resolvedType
    }
}

internal fun Type.asClass(): Class<*>? {
    return when {
        this is Class<*> -> this
        this is ParameterizedType -> this.rawType.asClass()
        this is GenericArrayType -> this.genericComponentType.asClass()?.arrayClass()
        this is TypeVariable<*> -> this.bounds.first().asClass()
        this is WildcardType -> this.upperBounds.first().asClass()
        else -> null
    }
}

internal fun Type.asArray(): Type? {
    return when {
        this is Class<*> -> this.arrayClass()
        this is ParameterizedType -> DeserializedGenericArrayType(this)
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
        else -> throw NotSerializableException("Don't know how to convert to ParameterizedType")
    }
}

internal fun Type.isSubClassOf(type: Type): Boolean {
    return TypeToken.of(this).isSubtypeOf(type)
}

// ByteArrays, primtives and boxed primitives are not stored in the object history
internal fun suitableForObjectReference(type: Type): Boolean {
    val clazz = type.asClass()
    return type != ByteArray::class.java && (clazz != null && !clazz.isPrimitive && !Primitives.unwrap(clazz).isPrimitive)
}

/**
 * Common properties that are to be used in the [SerializationContext.properties] to alter serialization behavior/content
 */
internal enum class CommonPropertyNames {
    IncludeInternalInfo,
}

/**
 * Utility function which helps tracking the path in the object graph when exceptions are thrown.
 * Since there might be a chain of nested calls it is useful to record which part of the graph caused an issue.
 * Path information is added to the message of the exception being thrown.
 */
internal inline fun <T> ifThrowsAppend(strToAppendFn: () -> String, block: () -> T): T {
    try {
        return block()
    } catch (th: Throwable) {
        th.setMessage("${strToAppendFn()} -> ${th.message}")
        throw th
    }
}

/**
 * Not a public property so will have to use reflection
 */
private fun Throwable.setMessage(newMsg: String) {
    val detailMessageField = Throwable::class.java.getDeclaredField("detailMessage")
    detailMessageField.isAccessible = true
    detailMessageField.set(this, newMsg)
}

fun ClassWhitelist.requireWhitelisted(type: Type) {
    if (!this.isWhitelisted(type.asClass()!!)) {
        throw NotSerializableException("Class $type is not on the whitelist or annotated with @CordaSerializable.")
    }
}

fun ClassWhitelist.isWhitelisted(clazz: Class<*>) = (hasListed(clazz) || hasAnnotationInHierarchy(clazz))
fun ClassWhitelist.isNotWhitelisted(clazz: Class<*>) = !(this.isWhitelisted(clazz))

// Recursively check the class, interfaces and superclasses for our annotation.
fun ClassWhitelist.hasAnnotationInHierarchy(type: Class<*>): Boolean {
    return type.isAnnotationPresent(CordaSerializable::class.java)
            || type.interfaces.any { hasAnnotationInHierarchy(it) }
            || (type.superclass != null && hasAnnotationInHierarchy(type.superclass))
}

/**
 * By default use Kotlin reflection and grab the objectInstance. However, that doesn't play nicely with nested
 * private objects. Even setting the accessibility override (setAccessible) still causes an
 * [IllegalAccessException] when attempting to retrieve the value of the INSTANCE field.
 *
 * Whichever reference to the class Kotlin reflection uses, override (set from setAccessible) on that field
 * isn't set even when it was explicitly set as accessible before calling into the kotlin reflection routines.
 *
 * For example
 *
 * clazz.getDeclaredField("INSTANCE")?.apply {
 *     isAccessible = true
 *     kotlin.objectInstance // This throws as the INSTANCE field isn't accessible
 * }
 *
 * Therefore default back to good old java reflection and simply look for the INSTANCE field as we are never going
 * to serialize a companion object.
 *
 * As such, if objectInstance fails access, revert to Java reflection and try that
 */
fun Class<*>.objectInstance() =
        try {
            this.kotlin.objectInstance
        } catch (e: IllegalAccessException) {
            // Check it really is an object (i.e. it has no constructor)
            if (constructors.isNotEmpty()) null
            else {
                try {
                    this.getDeclaredField("INSTANCE")?.let { field ->
                        // and must be marked as both static and final (>0 means they're set)
                        if (modifiers and Modifier.STATIC == 0 || modifiers and Modifier.FINAL == 0) null
                        else {
                            val accessibility = field.isAccessible
                            field.isAccessible = true
                            val obj = field.get(null)
                            field.isAccessible = accessibility
                            obj
                        }
                    }
                } catch (e: NoSuchFieldException) {
                    null
                }
            }
        }
