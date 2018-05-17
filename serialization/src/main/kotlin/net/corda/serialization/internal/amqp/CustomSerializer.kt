package net.corda.serialization.internal.amqp

import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.SerializationContext
import net.corda.serialization.internal.amqp.SerializerFactory.Companion.nameForType
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

interface SerializerFor {
    /**
     * This method should return true if the custom serializer can serialize an instance of the class passed as the
     * parameter.
     */
    fun isSerializerFor(clazz: Class<*>): Boolean

    val revealSubclassesInSchema: Boolean
}

/**
 * Base class for serializers of core platform types that do not conform to the usual serialization rules and thus
 * cannot be automatically serialized.
 */
abstract class CustomSerializer<T : Any> : AMQPSerializer<T>, SerializerFor {
    /**
     * This is a collection of custom serializers that this custom serializer depends on.  e.g. for proxy objects
     * that refer to other custom types etc.
     */
    open val additionalSerializers: Iterable<CustomSerializer<out Any>> = emptyList()


    protected abstract val descriptor: Descriptor
    /**
     * This exists purely for documentation and cross-platform purposes. It is not used by our serialization / deserialization
     * code path.
     */
    abstract val schemaForDocumentation: Schema

    /**
     * Whether subclasses using this serializer via inheritance should have a mapping in the schema.
     */
    override val revealSubclassesInSchema: Boolean get() = false

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                             context: SerializationContext, debugIndent: Int
    ) {
        data.withDescribed(descriptor) {
            writeDescribedObject(uncheckedCast(obj), data, type, output, context)
        }
    }

    abstract fun writeDescribedObject(obj: T, data: Data, type: Type, output: SerializationOutput,
                                      context: SerializationContext)

    /**
     * This custom serializer represents a sort of symbolic link from a subclass to a super class, where the super
     * class custom serializer is responsible for the "on the wire" format but we want to create a reference to the
     * subclass in the schema, so that we can distinguish between subclasses.
     */
    // TODO: should this be a custom serializer at all, or should it just be a plain AMQPSerializer?
    class SubClass<T : Any>(private val clazz: Class<*>, private val superClassSerializer: CustomSerializer<T>) : CustomSerializer<T>() {
        // TODO: should this be empty or contain the schema of the super?
        override val schemaForDocumentation = Schema(emptyList())

        override fun isSerializerFor(clazz: Class<*>): Boolean = clazz == this.clazz
        override val type: Type get() = clazz
        override val typeDescriptor: Symbol by lazy {
            Symbol.valueOf("$DESCRIPTOR_DOMAIN:${SerializerFingerPrinter().fingerprintForDescriptors(superClassSerializer.typeDescriptor.toString(), nameForType(clazz))}")
        }
        private val typeNotation: TypeNotation = RestrictedType(
                SerializerFactory.nameForType(clazz),
                null,
                emptyList(),
                SerializerFactory.nameForType(superClassSerializer.type),
                Descriptor(typeDescriptor),
                emptyList())

        override fun writeClassInfo(output: SerializationOutput) {
            output.writeTypeNotations(typeNotation)
        }

        override val descriptor: Descriptor = Descriptor(typeDescriptor)

        override fun writeDescribedObject(obj: T, data: Data, type: Type, output: SerializationOutput,
                                          context: SerializationContext
        ) {
            superClassSerializer.writeDescribedObject(obj, data, type, output, context)
        }

        override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput,
                                context: SerializationContext
        ): T {
            return superClassSerializer.readObject(obj, schemas, input, context)
        }
    }

    /**
     * Additional base features for a custom serializer for a particular class [withInheritance] is false
     * or super class / interfaces [withInheritance] is true
     */
    abstract class CustomSerializerImp<T : Any>(protected val clazz: Class<T>, protected val withInheritance: Boolean) : CustomSerializer<T>() {
        override val type: Type get() = clazz
        override val typeDescriptor: Symbol = Symbol.valueOf("$DESCRIPTOR_DOMAIN:${nameForType(clazz)}")
        override fun writeClassInfo(output: SerializationOutput) {}
        override val descriptor: Descriptor = Descriptor(typeDescriptor)
        override fun isSerializerFor(clazz: Class<*>): Boolean = if (withInheritance) this.clazz.isAssignableFrom(clazz) else this.clazz == clazz
    }

    /**
     * Additional base features for a custom serializer for a particular class, that excludes subclasses.
     */
    abstract class Is<T : Any>(clazz: Class<T>) : CustomSerializerImp<T>(clazz, false)

    /**
     * Additional base features for a custom serializer for all implementations of a particular interface or super class.
     */
    abstract class Implements<T : Any>(clazz: Class<T>) : CustomSerializerImp<T>(clazz, true)

    /**
     * Additional base features over and above [Implements] or [Is] custom serializer for when the serialized form should be
     * the serialized form of a proxy class, and the object can be re-created from that proxy on deserialization.
     *
     * The proxy class must use only types which are either native AMQP or other types for which there are pre-registered
     * custom serializers.
     */
    abstract class Proxy<T : Any, P : Any>(clazz: Class<T>,
                                           protected val proxyClass: Class<P>,
                                           protected val factory: SerializerFactory,
                                           withInheritance: Boolean = true) : CustomSerializerImp<T>(clazz, withInheritance) {
        override fun isSerializerFor(clazz: Class<*>): Boolean = if (withInheritance) this.clazz.isAssignableFrom(clazz) else this.clazz == clazz

        private val proxySerializer: ObjectSerializer by lazy { ObjectSerializer(proxyClass, factory) }

        override val schemaForDocumentation: Schema by lazy {
            val typeNotations = mutableSetOf<TypeNotation>(
                    CompositeType(
                            nameForType(type),
                            null,
                            emptyList(),
                            descriptor, (proxySerializer.typeNotation as CompositeType).fields))
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

        override fun writeDescribedObject(obj: T, data: Data, type: Type, output: SerializationOutput,
                                          context: SerializationContext
        ) {
            val proxy = toProxy(obj)
            data.withList {
                proxySerializer.propertySerializers.serializationOrder.forEach {
                    it.getter.writeProperty(proxy, this, output, context)
                }
            }
        }

        override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput,
                                context: SerializationContext
        ): T {
            val proxy: P = uncheckedCast(proxySerializer.readObject(obj, schemas, input, context))
            return fromProxy(proxy)
        }
    }

    /**
     * A custom serializer where the on-wire representation is a string.  For example, a [Currency] might be represented
     * as a 3 character currency code, and converted to and from that string.  By default, it is assumed that the
     * [toString] method will generate the string representation and that there is a constructor that takes such a
     * string as an argument to reconstruct.
     *
     * @param clazz The type to be marshalled
     * @param withInheritance Whether subclasses of the class can also be marshalled.
     * @param maker A lambda for constructing an instance, that defaults to calling a constructor that expects a string.
     * @param unmaker A lambda that extracts the string value for an instance, that defaults to the [toString] method.
     */
    abstract class ToString<T : Any>(clazz: Class<T>, withInheritance: Boolean = false,
                                     private val maker: (String) -> T = clazz.getConstructor(String::class.java).let { `constructor` ->
                                         { string -> `constructor`.newInstance(string) }
                                     },
                                     private val unmaker: (T) -> String = { obj -> obj.toString() })
        : CustomSerializerImp<T>(clazz, withInheritance) {

        override val schemaForDocumentation = Schema(
                listOf(RestrictedType(nameForType(type), "", listOf(nameForType(type)),
                        SerializerFactory.primitiveTypeName(String::class.java)!!,
                        descriptor, emptyList())))

        override fun writeDescribedObject(obj: T, data: Data, type: Type, output: SerializationOutput,
                                          context: SerializationContext
        ) {
            data.putString(unmaker(obj))
        }

        override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput,
                                context: SerializationContext
        ): T {
            val proxy = obj as String
            return maker(proxy)
        }
    }
}
