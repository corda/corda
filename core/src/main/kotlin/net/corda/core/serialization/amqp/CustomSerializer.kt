package net.corda.core.serialization.amqp

import net.corda.core.serialization.amqp.SerializerFactory.Companion.nameForType
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

/**
 * Base class for serializers of core platform types that do not conform to the usual serialization rules and thus
 * cannot be automatically serialized.
 */
abstract class CustomSerializer<T> : AMQPSerializer<T> {
    /**
     * This is a collection of custom serializers that this custom serializer depends on.  e.g. for proxy objects
     * that refer to other custom types etc.
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
            @Suppress("UNCHECKED_CAST")
            writeDescribedObject(obj as T, data, type, output)
        }
    }

    abstract fun writeDescribedObject(obj: T, data: Data, type: Type, output: SerializationOutput)

    // TODO: should this be a custom serializer at all, or should it just be a plain AMQPSerializer?
    class SubClass<T>(protected val clazz: Class<*>, protected val superClassSerializer: CustomSerializer<T>) : CustomSerializer<T>() {
        override val additionalSerializers: Iterable<CustomSerializer<out Any>> = emptyList()
        // TODO: should this be empty or contain the schema of the super?
        override val schemaForDocumentation = Schema(emptyList())

        override fun isSerializerFor(clazz: Class<*>): Boolean = clazz == this.clazz
        override val type: Type get() = clazz
        override val typeDescriptor: String = "$DESCRIPTOR_DOMAIN:${fingerprintForStrings(superClassSerializer.typeDescriptor, nameForType(clazz))}"
        private val typeNotation: TypeNotation = RestrictedType(SerializerFactory.nameForType(clazz), null, emptyList(), SerializerFactory.nameForType(superClassSerializer.type), Descriptor(typeDescriptor, null), emptyList())
        override fun writeClassInfo(output: SerializationOutput) {
            output.writeTypeNotations(typeNotation)
        }

        override val descriptor: Descriptor = Descriptor(typeDescriptor)

        override fun writeDescribedObject(obj: T, data: Data, type: Type, output: SerializationOutput) {
            superClassSerializer.writeDescribedObject(obj, data, type, output)
        }

        override fun readObject(obj: Any, schema: Schema, input: DeserializationInput): T {
            return superClassSerializer.readObject(obj, schema, input)
        }
    }

    /**
     * Additional base features for a custom serializer that is a particular class.
     */
    abstract class Is<T>(protected val clazz: Class<T>) : CustomSerializer<T>() {
        override fun isSerializerFor(clazz: Class<*>): Boolean = clazz == this.clazz
        override val type: Type get() = clazz
        override val typeDescriptor: String = "$DESCRIPTOR_DOMAIN:${nameForType(clazz)}"
        override fun writeClassInfo(output: SerializationOutput) {}
        override val descriptor: Descriptor = Descriptor(typeDescriptor)
    }

    /**
     * Additional base features for a custom serializer for all implementations of a particular interface or super class.
     */
    abstract class Implements<T>(protected val clazz: Class<T>) : CustomSerializer<T>() {
        override fun isSerializerFor(clazz: Class<*>): Boolean = this.clazz.isAssignableFrom(clazz)
        override val type: Type get() = clazz
        override val typeDescriptor: String = "$DESCRIPTOR_DOMAIN:${nameForType(clazz)}"
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
    abstract class Proxy<T, P>(protected val clazz: Class<T>,
                               protected val proxyClass: Class<P>,
                               protected val factory: SerializerFactory,
                               val withInheritance: Boolean = true) : CustomSerializer<T>() {
        override fun isSerializerFor(clazz: Class<*>): Boolean = if (withInheritance) this.clazz.isAssignableFrom(clazz) else this.clazz == clazz
        override val type: Type get() = clazz
        override val typeDescriptor: String = "$DESCRIPTOR_DOMAIN:${nameForType(clazz)}"
        override fun writeClassInfo(output: SerializationOutput) {}
        override val descriptor: Descriptor = Descriptor(typeDescriptor)

        private val proxySerializer: ObjectSerializer by lazy { ObjectSerializer(proxyClass, factory) }

        override val schemaForDocumentation: Schema by lazy {
            val typeNotations = mutableSetOf<TypeNotation>(CompositeType(nameForType(type), null, emptyList(), descriptor, (proxySerializer.typeNotation as CompositeType).fields))
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
            @Suppress("UNCHECKED_CAST")
            val proxy = proxySerializer.readObject(obj, schema, input) as P
            return fromProxy(proxy)
        }
    }

    abstract class ToString<T>(clazz: Class<T>, withInheritance: Boolean = false,
                               private val maker: (String) -> T = clazz.getConstructor(String::class.java).let { `constructor` -> { string -> `constructor`.newInstance(string) } },
                               private val unmaker: (T) -> String = { obj -> obj.toString() }) : Proxy<T, String>(clazz, String::class.java, /* Unused */ SerializerFactory(), withInheritance) {

        override val additionalSerializers: Iterable<CustomSerializer<out Any>> = emptyList()

        override val schemaForDocumentation = Schema(listOf(RestrictedType(nameForType(type), "", listOf(nameForType(type)), SerializerFactory.primitiveTypeName(String::class.java)!!, descriptor, emptyList())))

        override fun toProxy(obj: T): String = unmaker(obj)

        override fun fromProxy(proxy: String): T = maker(proxy)

        override fun writeDescribedObject(obj: T, data: Data, type: Type, output: SerializationOutput) {
            val proxy = toProxy(obj)
            data.putObject(proxy)
        }

        override fun readObject(obj: Any, schema: Schema, input: DeserializationInput): T {
            val proxy = input.readObject(obj, schema, String::class.java) as String
            return fromProxy(proxy)
        }
    }
}
