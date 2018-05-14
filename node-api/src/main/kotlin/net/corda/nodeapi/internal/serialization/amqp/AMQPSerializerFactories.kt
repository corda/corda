@file:JvmName("AMQPSerializerFactories")

package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.serialization.SerializationContext

fun createSerializerFactoryFactory(): SerializerFactoryFactory = SerializerFactoryFactoryImpl()

open class SerializerFactoryFactoryImpl : SerializerFactoryFactory {
    override fun make(context: SerializationContext) =
            SerializerFactory(context.whitelist, context.deserializationClassLoader)
}
