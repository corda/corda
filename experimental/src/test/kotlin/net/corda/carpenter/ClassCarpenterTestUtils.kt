package net.corda.carpenter.test

import net.corda.core.serialization.amqp.CompositeType
import net.corda.core.serialization.amqp.Field
import net.corda.core.serialization.amqp.Schema
import net.corda.core.serialization.amqp.TypeNotation
import net.corda.core.serialization.amqp.SerializerFactory
import net.corda.core.serialization.amqp.SerializationOutput

/**********************************************************************************************************************/

fun curruptName(name: String) = "${name}__carpenter"

/**********************************************************************************************************************/

/* given a list of class names work through the amqp envelope schema and alter any that
   match in the fashion defined above */
fun Schema.curruptName (names: List<String>) : Schema {
    val newTypes : MutableList<TypeNotation> = mutableListOf()

    for (type in types) {
        val newName = if (type.name in names) curruptName (type.name) else type.name

        val newProvides = type.provides.map {
            it -> if (it in names) curruptName (it) else it
        }

        val newFields = mutableListOf<Field>()

        (type as CompositeType).fields.forEach {
            val type = if (it.type in names) curruptName (it.type) else it.type

            val requires = if (it.requires.isNotEmpty() && (it.requires[0] in names))
                listOf (curruptName (it.requires[0])) else it.requires

            newFields.add (it.copy (type=type, requires=requires))
        }

        newTypes.add (type.copy (name=newName, provides=newProvides, fields=newFields))
    }

    return Schema (types=newTypes)
}

/**********************************************************************************************************************/

open class AmqpCarpenterBase {
    var factory = SerializerFactory()

    fun serialise (clazz : Any) = SerializationOutput(factory).serialize(clazz)
    fun testName() = Thread.currentThread().stackTrace[2].methodName
    inline fun classTestName(clazz: String) = "${this.javaClass.name}\$${testName()}\$$clazz"
}

/**********************************************************************************************************************/

