package net.corda.core.serialization.amqp

import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Array
import java.lang.reflect.Type

abstract class CustomSerializer<T> : AMQPSerializer<T> {
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

    abstract class Is<T>(protected val clazz: Class<T>) : CustomSerializer<T>() {
        override fun isSerializerFor(clazz: Class<*>): Boolean = clazz == this.clazz
        override val type: Type get() = clazz
        override val typeDescriptor: String = "$DESCRIPTOR_DOMAIN:${clazz.simpleName}"
        override fun writeClassInfo(output: SerializationOutput) {}
        override val descriptor: Descriptor = Descriptor(typeDescriptor)
    }

    abstract class Implements<T>(protected val clazz: Class<T>) : CustomSerializer<T>() {
        override fun isSerializerFor(clazz: Class<*>): Boolean = this.clazz.isAssignableFrom(clazz)
        override val type: Type get() = clazz
        override val typeDescriptor: String = "$DESCRIPTOR_DOMAIN:${clazz.simpleName}"
        override fun writeClassInfo(output: SerializationOutput) {}
        override val descriptor: Descriptor = Descriptor(typeDescriptor)
    }

    abstract class Proxy<T, P>(protected val clazz: Class<T>, protected val proxyClass: Class<P>, protected val factory: SerializerFactory, val withInheritance: Boolean = true) : CustomSerializer<T>() {
        override fun isSerializerFor(clazz: Class<*>): Boolean = if (withInheritance) this.clazz.isAssignableFrom(clazz) else this.clazz == clazz
        override val type: Type get() = clazz
        override val typeDescriptor: String = "$DESCRIPTOR_DOMAIN:${clazz.simpleName}"
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

        protected abstract fun toProxy(obj: T): P
        protected abstract fun fromProxy(proxy: P): T

        override fun writeDescribedObject(obj: T, data: Data, type: Type, output: SerializationOutput) {
            val proxy = toProxy(obj)
            data.withList {
                val selfContainedOuput = SerializationOutput(output.serializerFactory)
                for (property in proxySerializer.propertySerializers) {
                    property.writeProperty(proxy, this, selfContainedOuput)
                }
            }
        }

        override fun readObject(obj: Any, schema: Schema, input: DeserializationInput): T {
            val proxy = proxySerializer.readObject(obj, schema, DeserializationInput(input.serializerFactory)) as P
            return fromProxy(proxy)
        }
    }

    abstract class PredefinedArray<T>(protected val elementClazz: Class<T>, private val factory: SerializerFactory) : CustomSerializer<kotlin.Array<T>>() {
        override val additionalSerializers: Iterable<CustomSerializer<out Any>> get() = listOf(factory.get(elementClazz, elementClazz) as CustomSerializer<out Any>)

        private val arrayClazz = Array.newInstance(elementClazz, 0).javaClass

        override fun isSerializerFor(clazz: Class<*>): Boolean = clazz == arrayClazz

        override val type: Type get() = arraySerializer.type
        override val typeDescriptor: String = "$DESCRIPTOR_DOMAIN:${arrayClazz.simpleName}"
        override fun writeClassInfo(output: SerializationOutput) {}
        override val descriptor: Descriptor = Descriptor(typeDescriptor)

        private val arraySerializer: ArraySerializer by lazy { ArraySerializer(arrayClazz, factory) }

        override val schemaForDocumentation: Schema = Schema(listOf(RestrictedType(arrayClazz.name, null, emptyList(), "list", Descriptor(typeDescriptor, null), emptyList())))

        override fun writeDescribedObject(obj: kotlin.Array<T>, data: Data, type: Type, output: SerializationOutput) {
            data.withList {
                for (entry in obj) {
                    output.writeObjectOrNull(entry, this, arraySerializer.elementType)
                }
            }
        }

        override fun readObject(obj: Any, schema: Schema, input: DeserializationInput): kotlin.Array<T> {
            return arraySerializer.readObject(obj, schema, DeserializationInput(input.serializerFactory)) as kotlin.Array<T>
        }
    }
}
