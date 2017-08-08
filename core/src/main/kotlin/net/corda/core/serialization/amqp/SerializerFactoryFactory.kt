package net.corda.core.serialization.amqp

import net.corda.core.serialization.AllWhitelist
import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.SerializationContext

/**
 * Factory singleton that maps unique Serilizer Factories from a pair of WhitleList ad ClassLoader
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
