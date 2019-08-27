@file:JvmName("AMQPSerializerFactories")

package net.corda.serialization.internal.amqp

import net.corda.core.serialization.SerializationContext
import net.corda.serialization.internal.carpenter.ClassCarpenter
import net.corda.serialization.internal.carpenter.ClassCarpenterImpl

fun createSerializerFactoryFactory(): SerializerFactoryFactory = SerializerFactoryFactoryImpl()

fun createClassCarpenter(context: SerializationContext): ClassCarpenter = ClassCarpenterImpl(
    whitelist = context.whitelist,
    cl = context.deserializationClassLoader,
    lenient = context.lenientCarpenterEnabled
)

open class SerializerFactoryFactoryImpl : SerializerFactoryFactory {
    override fun make(context: SerializationContext): SerializerFactory {
        return SerializerFactoryBuilder.build(context.whitelist,
                createClassCarpenter(context),
                mustPreserveDataWhenEvolving = context.preventDataLoss
        )
    }
}
