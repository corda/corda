/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.serialization.carpenter

import net.corda.core.serialization.ClassWhitelist
import net.corda.nodeapi.internal.serialization.amqp.*
import net.corda.nodeapi.internal.serialization.amqp.Field
import net.corda.nodeapi.internal.serialization.amqp.Schema
import net.corda.nodeapi.internal.serialization.amqp.testutils.serialize

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

/**
 * Custom implementation of a [SerializerFactory] where we need to give it a class carpenter
 * rather than have it create its own
 */
class SerializerFactoryExternalCarpenter(classCarpenter: ClassCarpenter)
    : SerializerFactory(classCarpenter.whitelist, classCarpenter)

open class AmqpCarpenterBase(whitelist: ClassWhitelist) {
    var cc = ClassCarpenterImpl(whitelist = whitelist)
    var factory = SerializerFactoryExternalCarpenter(cc)

    fun serialise(clazz: Any) = SerializationOutput(factory).serialize(clazz)
    fun testName(): String = Thread.currentThread().stackTrace[2].methodName
    @Suppress("NOTHING_TO_INLINE")
    inline fun classTestName(clazz: String) = "${this.javaClass.name}\$${testName()}\$$clazz"
}
