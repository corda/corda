@file:JvmName("AMQPSerializerFactories")
package net.corda.serialization.internal.amqp

import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.SerializationContext
import net.corda.serialization.internal.carpenter.ClassCarpenter
import net.corda.serialization.internal.carpenter.Schema

/**
 * Creates a [SerializerFactoryFactory] suitable for the DJVM,
 * i.e. one without a [ClassCarpenter] implementation.
 */
@Suppress("UNUSED")
fun createSerializerFactoryFactory(): SerializerFactoryFactory = DeterministicSerializerFactoryFactory()

private class DeterministicSerializerFactoryFactory : SerializerFactoryFactory {
    override fun make(context: SerializationContext) =
        SerializerFactory(
            whitelist = context.whitelist,
            classCarpenter = DummyClassCarpenter(context.whitelist, context.deserializationClassLoader),
            serializersByType = mutableMapOf(),
            serializersByDescriptor = mutableMapOf(),
            customSerializers = ArrayList(),
            transformsCache = mutableMapOf()
        )
}

private class DummyClassCarpenter(
    override val whitelist: ClassWhitelist,
    override val classloader: ClassLoader
) : ClassCarpenter {
    override fun build(schema: Schema): Class<*>
        = throw UnsupportedOperationException("ClassCarpentry not supported")
}