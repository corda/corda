package net.corda.core.serialization.carpenter

import net.corda.core.serialization.amqp.Field
import net.corda.core.serialization.amqp.Schema
import net.corda.core.serialization.amqp.SerializerFactory
import net.corda.core.serialization.amqp.SerializationOutput

fun mangleName(name: String) = "${name}__carpenter"

/**
 * given a list of class names work through the amqp envelope schema and alter any that
 * match in the fashion defined above
 */
fun Schema.mangleNames(names: List<String>): Schema {
    val newTypes: MutableList<TypeNotation> = mutableListOf()

    for (type in types) {
        val newName = if (type.name in names) mangleName(type.name) else type.name
        val newProvides = type.provides.map { if (it in names) mangleName(it) else it }
        val newFields = mutableListOf<Field>()

        (type as CompositeType).fields.forEach {
            val fieldType = if (it.type in names) mangleName(it.type) else it.type
            val requires =
                    if (it.requires.isNotEmpty() && (it.requires[0] in names)) listOf(mangleName(it.requires[0]))
                    else it.requires

            newFields.add(it.copy(type = fieldType, requires = requires))
        }

        newTypes.add(type.copy(name = newName, provides = newProvides, fields = newFields))
    }

    return Schema(types = newTypes)
}

open class AmqpCarpenterBase {
    var factory = SerializerFactory()

    fun serialise(clazz: Any) = SerializationOutput(factory).serialize(clazz)
    fun testName(): String = Thread.currentThread().stackTrace[2].methodName
    @Suppress("NOTHING_TO_INLINE")
    inline fun classTestName(clazz: String) = "${this.javaClass.name}\$${testName()}\$$clazz"
}
