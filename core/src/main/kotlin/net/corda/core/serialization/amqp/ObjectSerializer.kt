package net.corda.core.serialization.amqp

import org.apache.qpid.proton.amqp.UnsignedInteger
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.Constructor
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.jvm.javaConstructor

/**
 * Responsible for serializing and deserializing a regular object instance via a series of properties (matched with a constructor).
 */
class ObjectSerializer(val clazz: Class<*>) : AMQPSerializer {
    override val type: Type get() = clazz
    private val javaConstructor: Constructor<Any>?
    private val propertySerializers: Collection<PropertySerializer>

    init {
        val kotlinConstructor = constructorForDeserialization(clazz)
        javaConstructor = kotlinConstructor?.javaConstructor
        propertySerializers = propertiesForSerialization(kotlinConstructor, clazz)
    }
    private val typeName = clazz.name
    override val typeDescriptor = "net.corda:${hashType(type)}"
    private val interfaces = generateInterfaces(clazz) // TODO maybe this proves too much and we need annotations to restrict.

    private val typeNotation: TypeNotation = CompositeType(typeName, null, generateProvides(), Descriptor(typeDescriptor, null), generateFields())

    override fun writeClassInfo(output: SerializationOutput) {
        output.writeTypeNotations(typeNotation)
        for (iface in interfaces) {
            output.requireSerializer(iface)
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

    override fun readObject(obj: Any, envelope: Envelope, input: DeserializationInput): Any {
        if (obj is UnsignedInteger) {
            // TODO: Object refs
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        } else if (obj is List<*>) {
            if (obj.size > propertySerializers.size) throw NotSerializableException("Too many properties in described type $typeName")
            val params = obj.zip(propertySerializers).map { it.second.readProperty(it.first, envelope, input) }
            return construct(params)
        } else throw NotSerializableException("Body of described type is unexpected $obj")
    }

    private fun generateFields(): List<Field> {
        return propertySerializers.map { Field(it.name, it.type, it.requires, it.default, null, it.mandatory, false) }
    }

    private fun generateProvides(): List<String> {
        return interfaces.map { it.typeName }
    }

    private fun generateInterfaces(clazz: Class<*>): List<Type> {
        val interfaces = mutableSetOf<Type>()
        exploreType(clazz, interfaces)
        return interfaces.toList()
    }

    private fun exploreType(type: Type?, interfaces: MutableSet<Type>) {
        val clazz = (type as? Class<*>) ?: (type as? ParameterizedType)?.rawType as? Class<*>
        if (clazz != null) {
            for (newInterface in clazz.genericInterfaces) {
                if (newInterface !in interfaces) {
                    interfaces += newInterface
                    exploreType(newInterface, interfaces)
                }
            }
            exploreType(clazz.genericSuperclass, interfaces)
        }
    }

    fun construct(properties: List<Any?>): Any {
        if (javaConstructor == null) {
            throw NotSerializableException("Attempt to deserialize an interface: $clazz. Serialized form is invalid.")
        }
        return javaConstructor.newInstance(*properties.toTypedArray())
    }
}