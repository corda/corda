package net.corda.core.serialization.amqp

import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

/**
 * Base class for serializers of core platform types that do not conform to the usual serialization rules and thus
 * cannot be automatically serialized.
 */
abstract class CustomSerializer<T> : AMQPSerializer<T> {
    /**
     * This is a collection of custom serializers that this custom serializer depends on.  e.g. for proxy objects
     * that refer to arrays of types etc.
     */
    abstract val additionalSerializers: Iterable<CustomSerializer<out Any>>

    abstract fun isSerializerFor(clazz: Class<*>): Boolean
    protected abstract val descriptor: Descriptor
    /**
     * This exists purely for documentation and cross-platform purposes. It is not used by our serialization / deserialization
     * code path.
     */
    abstract val schemaForDocumentation: Schema

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        data.withDescribed(descriptor) {
            writeDescribedObject(obj as T, data, type, output)
        }
    }

    abstract fun writeDescribedObject(obj: T, data: Data, type: Type, output: SerializationOutput)

    /**
     * Additional base features for a custom serializer that is a particular class.
     */
    abstract class Is<T>(protected val clazz: Class<T>) : CustomSerializer<T>() {
        override fun isSerializerFor(clazz: Class<*>): Boolean = clazz == this.clazz
        override val type: Type get() = clazz
        override val typeDescriptor: String = "$DESCRIPTOR_DOMAIN:${clazz.name}"
        override fun writeClassInfo(output: SerializationOutput) {}
        override val descriptor: Descriptor = Descriptor(typeDescriptor)
    }

    /**
     * Additional base features for a custom serializer for all implementations of a particular interface or super class.
     */
    abstract class Implements<T>(protected val clazz: Class<T>) : CustomSerializer<T>() {
        override fun isSerializerFor(clazz: Class<*>): Boolean = this.clazz.isAssignableFrom(clazz)
        override val type: Type get() = clazz
        override val typeDescriptor: String = "$DESCRIPTOR_DOMAIN:${clazz.name}"
        override fun writeClassInfo(output: SerializationOutput) {}
        override val descriptor: Descriptor = Descriptor(typeDescriptor)
    }

    /**
     * Addition base features over and above [Implements] or [Is] custom serializer for when the serialize form should be
     * the serialized form of a proxy class, and the object can be re-created from that proxy on deserialization.
     *
     * The proxy class must use only types which are either native AMQP or other types for which there are pre-registered
     * custom serializers.
     */
    abstract class Proxy<T, P>(protected val clazz: Class<T>, protected val proxyClass: Class<P>, protected val factory: SerializerFactory, val withInheritance: Boolean = true) : CustomSerializer<T>() {
        override fun isSerializerFor(clazz: Class<*>): Boolean = if (withInheritance) this.clazz.isAssignableFrom(clazz) else this.clazz == clazz
        override val type: Type get() = clazz
        override val typeDescriptor: String = "$DESCRIPTOR_DOMAIN:${clazz.name}"
        override fun writeClassInfo(output: SerializationOutput) {}
        override val descriptor: Descriptor = Descriptor(typeDescriptor)

        private val proxySerializer: ObjectSerializer by lazy { ObjectSerializer(proxyClass, factory) }

        override val schemaForDocumentation: Schema by lazy {
            val typeNotations = mutableSetOf<TypeNotation>(CompositeType(type.typeName, null, emptyList(), descriptor, (proxySerializer.typeNotation as CompositeType).fields))
            for (additional in additionalSerializers) {
                typeNotations.addAll(additional.schemaForDocumentation.types)
            }
            Schema(typeNotations.toList())
        }

        /**
         * Implement these two methods.
         */
        protected abstract fun toProxy(obj: T): P

        protected abstract fun fromProxy(proxy: P): T

        override fun writeDescribedObject(obj: T, data: Data, type: Type, output: SerializationOutput) {
            val proxy = toProxy(obj)
            data.withList {
                for (property in proxySerializer.propertySerializers) {
                    property.writeProperty(proxy, this, output)
                }
            }
        }

        override fun readObject(obj: Any, schema: Schema, input: DeserializationInput): T {
            val proxy = proxySerializer.readObject(obj, schema, input) as P
            return fromProxy(proxy)
        }
    }
}
