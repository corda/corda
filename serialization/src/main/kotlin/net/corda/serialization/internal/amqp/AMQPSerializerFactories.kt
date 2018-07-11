@file:JvmName("AMQPSerializerFactories")

package net.corda.serialization.internal.amqp

import net.corda.core.serialization.SerializationContext

fun createSerializerFactoryFactory(): SerializerFactoryFactory = SerializerFactoryFactoryImpl()

open class SerializerFactoryFactoryImpl : SerializerFactoryFactory {
    override fun make(context: SerializationContext): SerializerFactory {
        return SerializerFactory(context.whitelist, context.deserializationClassLoader, context.lenientCarpenterEnabled)
    }
}
