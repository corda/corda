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

/**
 * Creates a [ClassCarpenter] suitable for the DJVM, i.e. one that doesn't work.
 */
fun createClassCarpenter(context: SerializationContext): ClassCarpenter = DummyClassCarpenter(context.whitelist, context.deserializationClassLoader)

private class DeterministicSerializerFactoryFactory : SerializerFactoryFactory {
    override fun make(context: SerializationContext) =
            SerializerFactoryBuilder.build(
            whitelist = context.whitelist,
            classCarpenter = createClassCarpenter(context))
}

private class DummyClassCarpenter(
    override val whitelist: ClassWhitelist,
    override val classloader: ClassLoader
) : ClassCarpenter {
    override fun build(schema: Schema): Class<*>
        = throw UnsupportedOperationException("ClassCarpentry not supported")
}