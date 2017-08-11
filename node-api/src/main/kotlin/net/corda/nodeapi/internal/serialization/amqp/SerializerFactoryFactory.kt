package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.ClassWhitelist
import net.corda.nodeapi.internal.serialization.AllWhitelist

/**
 * Factory singleton that maps unique Serializer Factories from a pair of WhitleList and ClassLoader
 */
object SerializerFactoryFactory {
    val factories : MutableMap<Pair<ClassWhitelist, ClassLoader>, SerializerFactory> = mutableMapOf()

    fun get(context: SerializationContext) : SerializerFactory =
            factories.computeIfAbsent(Pair(context.whitelist, context.deserializationClassLoader)) {
                SerializerFactory(context.whitelist, context.deserializationClassLoader)
            }

    fun get() : SerializerFactory =
            factories.computeIfAbsent(Pair(AllWhitelist, ClassLoader.getSystemClassLoader())) {
                SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader())
            }
}
