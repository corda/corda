package net.corda.core.serialization.amqp

import org.apache.qpid.proton.codec.Data
import java.beans.Introspector
import java.beans.PropertyDescriptor
import java.io.NotSerializableException
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaConstructor


class ClassSerializer(val clazz: Class<*>, serializerFactory: SerializerFactory) : Serializer() {
    private val propertySerializers = generatePropertySerializers(clazz)
    private val typeName = clazz.name
    private val typeDescriptor = clazz.name // TODO need a better algo
    private val interfaces = generateInterfaces(clazz) // TODO maybe this proves too much and we need annotations.

    private val typeNotation: TypeNotation = CompositeType(typeName, null, generateProvides(), Descriptor(typeDescriptor, null), generateFields())

    override fun writeClassInfo(output: SerializationOutput) {
        output.writeTypeNotations(typeNotation)
        for (iface in interfaces) {
            output.requireSerializer(iface)
        }
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        // Write described
        data.putDescribed()
        data.enter()
        // Write descriptor
        data.putObject(typeNotation.descriptor.code ?: typeNotation.descriptor.name)
        // Write list
        data.putList()
        data.enter()
        for (property in propertySerializers) {
            property.writeProperty(obj, data, output)
        }
        data.exit() // exit list
        data.exit() // exit described
    }

    private fun generateFields(): List<Field> {
        return propertySerializers.map { Field(it.name, it.type, it.requires, it.default, null, it.mandatory, false) }
    }

    private fun generateProvides(): Array<String>? {
        return if (interfaces.isEmpty()) null else interfaces.map { it.typeName }.toTypedArray()
    }

    private fun generateInterfaces(clazz: Class<*>): Array<Type> {
        val interfaces = mutableSetOf<Type>()
        exploreType(clazz, interfaces)
        return interfaces.toTypedArray()
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

    private fun generatePropertySerializers(clazz: Class<*>): List<PropertySerializer> {
        val properties = sortAndFilterProperties(Introspector.getBeanInfo(clazz).propertyDescriptors)
        return properties.map { makePropertySerializer(it) }
    }

    // Sort alphabetically and/or apply annotation based customisations.
    private fun sortAndFilterProperties(propertyDescriptors: Array<PropertyDescriptor>): List<PropertyDescriptor> {
        return propertyDescriptors.filter { it.name != "class" }.sortedBy { it.name }
    }

    private fun makePropertySerializer(property: PropertyDescriptor): PropertySerializer {
        if (property.writeMethod != null) throw NotSerializableException("Property ${property.name} is read/write for $clazz.")
        return PropertySerializer.make(property.name, property.readMethod)
    }

    fun isConcrete(): Boolean = !(clazz.isInterface || Modifier.isAbstract(clazz.modifiers))

    // TODO: Only supports kotlin for now...
    // TODO: Include type in the mapping, not just name!
    private val javaConstructor = if (isConcrete()) clazz.kotlin.primaryConstructor!!.javaConstructor!! else null
    private val constructorParamIndexes = if (isConcrete()) makeParamMapping(clazz.kotlin.primaryConstructor!!.parameters.map { it.name!! }) else null

    private fun makeParamMapping(constructorParamNames: List<String>): List<Int> {
        val paramNameToIndex = mutableMapOf<String, Int>()
        var index = 0
        for (field in propertySerializers) {
            paramNameToIndex[field.name] = index++
        }
        val indexes = mutableListOf<Int>()
        for (paramName in constructorParamNames) {
            indexes += paramNameToIndex[paramName] ?: throw NotSerializableException("Could not find property matching constructor parameter $paramName for $clazz")
        }
        if (indexes.size != propertySerializers.size) throw NotSerializableException("Number of properties not equal to number of primary constructor parameter for $clazz")
        return indexes
    }

    fun construct(properties: List<Any?>): Any {
        if (javaConstructor == null) {
            throw NotSerializableException("Attempt to deserialize an interface: $clazz. Serialized form is invalid.")
        }
        val params = Array<Any?>(properties.size) {
            properties[constructorParamIndexes!![it]]
        }
        return javaConstructor.newInstance(*params)
    }
}