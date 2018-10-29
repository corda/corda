@file:JvmName("AMQPSerializerFactories")

package net.corda.serialization.internal.amqp

import net.corda.core.serialization.SerializationContext
import net.corda.serialization.internal.carpenter.ClassCarpenterImpl

fun createSerializerFactoryFactory(): SerializerFactoryFactory = SerializerFactoryFactoryImpl()

open class SerializerFactoryFactoryImpl : SerializerFactoryFactory {
    override fun make(context: SerializationContext): SerializerFactory {
        return SerializerFactoryBuilder.build(context.whitelist,
                ClassCarpenterImpl(context.whitelist, context.deserializationClassLoader, context.lenientCarpenterEnabled)
        )
    }
}
