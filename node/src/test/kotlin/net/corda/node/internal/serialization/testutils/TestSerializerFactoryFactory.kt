package net.corda.node.internal.serialization.testutils

import net.corda.core.serialization.SerializationContext
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactoryFactory

class TestSerializerFactoryFactory : SerializerFactoryFactory() {
    override fun make(context: SerializationContext): SerializerFactory {
        return super.make(context)
    }
}