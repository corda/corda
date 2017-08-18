package net.corda.nodeapi.internal.serialization.amqp

import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory.Companion.nameForType
import org.apache.qpid.proton.amqp.UnsignedInteger
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.Constructor
import java.lang.reflect.Type
import kotlin.reflect.jvm.javaConstructor

/**
 * Responsible for serializing and deserializing a regular object instance via a series of properties (matched with a constructor).
 */
open class ObjectSerializer(val clazz: Type, factory: SerializerFactory) : AMQPSerializer<Any> {
    override val type: Type get() = clazz
    open internal val propertySerializers: Collection<PropertySerializer>
    open val kotlinConstructor = constructorForDeserialization(clazz)

    init {
        propertySerializers = propertiesForSerialization(kotlinConstructor, clazz, factory)
    }

    private val typeName = nameForType(clazz)

    override val typeDescriptor = "$DESCRIPTOR_DOMAIN:${fingerprintForType(type, factory)}"
    private val interfaces = interfacesForSerialization(clazz, factory) // We restrict to only those annotated or whitelisted

    open internal val typeNotation : TypeNotation by lazy {CompositeType(typeName, null, generateProvides(), Descriptor(typeDescriptor, null), generateFields()) }

    override fun writeClassInfo(output: SerializationOutput) {
        if (output.writeTypeNotations(typeNotation)) {
            for (iface in interfaces) {
                output.requireSerializer(iface)
            }
            for (property in propertySerializers) {
                property.writeClassInfo(output)
            }
        }
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        // Write described
        data.withDescribed(typeNotation.descriptor) {
            // Write list
            withList {
                for (property in propertySerializers) {
                    property.writeProperty(obj, this, output)
                }
            }
        }
    }

    override fun readObject(obj: Any, schema: Schema, input: DeserializationInput): Any {
        if (obj is UnsignedInteger) {
            // TODO: Object refs
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        } else if (obj is List<*>) {
            if (obj.size > propertySerializers.size) throw NotSerializableException("Too many properties in described type $typeName")
            val params = obj.zip(propertySerializers).map { it.second.readProperty(it.first, schema, input) }
            return construct(params)
        } else throw NotSerializableException("Body of described type is unexpected $obj")
    }

    private fun generateFields(): List<Field> {
        return propertySerializers.map { Field(it.name, it.type, it.requires, it.default, null, it.mandatory, false) }
    }

    private fun generateProvides(): List<String> {
        return interfaces.map { nameForType(it) }
    }


    fun construct(properties: List<Any?>): Any {
        val javaConstructor = kotlinConstructor?.javaConstructor ?:
            throw NotSerializableException("Attempt to deserialize an interface: $clazz. Serialized form is invalid.")

        return javaConstructor.newInstance(*properties.toTypedArray())
    }
}
