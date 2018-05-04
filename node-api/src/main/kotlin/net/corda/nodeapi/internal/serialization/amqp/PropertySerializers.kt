package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.utilities.loggerFor
import java.io.NotSerializableException
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Type
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.kotlinProperty

abstract class PropertyReader {
    abstract fun read(obj: Any?): Any?
    abstract fun isNullable(): Boolean
}

/**
 * Accessor for those properties of a class that have defined getter functions.
 */
class PublicPropertyReader(private val readMethod: Method?) : PropertyReader() {
    init {
        readMethod?.isAccessible = true
    }

    private fun Method.returnsNullable(): Boolean {
        try {
            val returnTypeString = this.declaringClass.kotlin.memberProperties.firstOrNull {
                it.javaGetter == this
            }?.returnType?.toString() ?: "?"

            return returnTypeString.endsWith('?') || returnTypeString.endsWith('!')
        } catch (e: kotlin.reflect.jvm.internal.KotlinReflectionInternalError) {
            // This might happen for some types, e.g. kotlin.Throwable? - the root cause of the issue
            // is: https://youtrack.jetbrains.com/issue/KT-13077
            // TODO: Revisit this when Kotlin issue is fixed.

            // So this used to report as an error, but given we serialise exceptions all the time it
            // provides for very scary log files so move this to trace level
            loggerFor<PropertySerializer>().let { logger ->
                logger.trace("Using kotlin introspection on internal type ${this.declaringClass}")
                logger.trace("Unexpected internal Kotlin error", e)
            }
            return true
        }
    }

    override fun read(obj: Any?): Any? {
        return readMethod!!.invoke(obj)
    }

    override fun isNullable(): Boolean = readMethod?.returnsNullable() ?: false
}

/**
 * Accessor for those properties of a class that do not have defined getter functions. In which case
 * we used reflection to remove the unreadable status from that property whilst it's accessed.
 */
class PrivatePropertyReader(val field: Field, parentType: Type) : PropertyReader() {
    init {
        loggerFor<PropertySerializer>().warn("Create property Serializer for private property '${field.name}' not "
                + "exposed by a getter on class '$parentType'\n"
                + "\tNOTE: This behaviour will be deprecated at some point in the future and a getter required")
    }

    override fun read(obj: Any?): Any? {
        field.isAccessible = true
        val rtn = field.get(obj)
        field.isAccessible = false
        return rtn
    }

    override fun isNullable() = try {
        field.kotlinProperty?.returnType?.isMarkedNullable ?: false
    } catch (e: kotlin.reflect.jvm.internal.KotlinReflectionInternalError) {
        // This might happen for some types, e.g. kotlin.Throwable? - the root cause of the issue
        // is: https://youtrack.jetbrains.com/issue/KT-13077
        // TODO: Revisit this when Kotlin issue is fixed.

        // So this used to report as an error, but given we serialise exceptions all the time it
        // provides for very scary log files so move this to trace level
        loggerFor<PropertySerializer>().let { logger ->
            logger.trace("Using kotlin introspection on internal type ${field}")
            logger.trace("Unexpected internal Kotlin error", e)
        }
        true
    }
}

/**
 * Special instance of a [PropertyReader] for use only by [EvolutionSerializer]s to make
 * it explicit that no properties are ever actually read from an object as the evolution
 * serializer should only be accessing the already serialized form.
 */
class EvolutionPropertyReader : PropertyReader() {
    override fun read(obj: Any?): Any? {
        throw UnsupportedOperationException("It should be impossible for an evolution serializer to "
                + "be reading from an object")
    }

    override fun isNullable() = true
}

/**
 * Represents a generic interface to a serializable property of an object.
 *
 * @property initialPosition where in the constructor used for serialization the property occurs.
 * @property getter a [PropertySerializer] wrapping access to the property. This will either be a
 * method invocation on the getter or, if not publicly accessible, reflection based by temporally
 * making the property accessible.
 */
abstract class PropertyAccessor(
        val initialPosition: Int,
        open val getter: PropertySerializer) {
    companion object : Comparator<PropertyAccessor> {
        override fun compare(p0: PropertyAccessor?, p1: PropertyAccessor?): Int {
            return p0?.getter?.name?.compareTo(p1?.getter?.name ?: "") ?: 0
        }
    }

    /**
     * Override to control how the property is set on the object.
     */
    abstract fun set(instance: Any, obj: Any?)

    override fun toString(): String {
        return "${getter.name}($initialPosition)"
    }
}

/**
 * Implementation of [PropertyAccessor] representing a property of an object that
 * is serialized and deserialized via JavaBean getter and setter style methods.
 */
class PropertyAccessorGetterSetter(
        initialPosition: Int,
        getter: PropertySerializer,
        private val setter: Method) : PropertyAccessor(initialPosition, getter) {
    init {
        /**
         * Play nicely with Java interop, public methods aren't marked as accessible
         */
        setter.isAccessible = true
    }

    /**
     * Invokes the setter on the underlying object passing in the serialized value.
     */
    override fun set(instance: Any, obj: Any?) {
        setter.invoke(instance, *listOf(obj).toTypedArray())
    }
}

/**
 * Implementation of [PropertyAccessor] representing a property of an object that
 * is serialized via a JavaBean getter but deserialized using the constructor
 * of the object the property belongs to.
 */
class PropertyAccessorConstructor(
        initialPosition: Int,
        override val getter: PropertySerializer) : PropertyAccessor(initialPosition, getter) {
    /**
     * Because the property should be being set on the obejct through the constructor any
     * calls to the explicit setter should be an error.
     */
    override fun set(instance: Any, obj: Any?) {
        NotSerializableException("Attempting to access a setter on an object being instantiated " +
                "via its constructor.")
    }
}

/**
 * Represents a collection of [PropertyAccessor]s that represent the serialized form
 * of an object.
 *
 * @property serializationOrder a list of [PropertyAccessor]. For deterministic serialization
 * should be sorted.
 * @property size how many properties are being serialized.
 * @property byConstructor are the properties of the class represented by this set of properties populated
 * on deserialization via the object's constructor or the corresponding setter functions. Should be
 * overridden and set appropriately by child types.
 */
abstract class PropertySerializers(
        val serializationOrder: List<PropertyAccessor>) {
    companion object {
        fun make(serializationOrder: List<PropertyAccessor>) =
                when (serializationOrder.firstOrNull()) {
                    is PropertyAccessorConstructor -> PropertySerializersConstructor(serializationOrder)
                    is PropertyAccessorGetterSetter -> PropertySerializersSetter(serializationOrder)
                    null -> PropertySerializersNoProperties()
                    else -> {
                        throw NotSerializableException("Unknown Property Accessor type, cannot create set")
                    }
                }
    }

    val size get() = serializationOrder.size
    abstract val byConstructor: Boolean
}

class PropertySerializersNoProperties : PropertySerializers(emptyList()) {
    override val byConstructor get() = true
}

class PropertySerializersConstructor(
        serializationOrder: List<PropertyAccessor>) : PropertySerializers(serializationOrder) {
    override val byConstructor get() = true
}

class PropertySerializersSetter(
        serializationOrder: List<PropertyAccessor>) : PropertySerializers(serializationOrder) {
    override val byConstructor get() = false
}

class PropertySerializersEvolution : PropertySerializers(emptyList()) {
    override val byConstructor get() = false
}


