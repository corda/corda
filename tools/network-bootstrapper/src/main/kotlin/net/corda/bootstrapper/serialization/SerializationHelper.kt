package net.corda.bootstrapper.serialization

import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.node.serialization.amqp.AMQPServerSerializationScheme
import net.corda.serialization.internal.AMQP_P2P_CONTEXT
import net.corda.serialization.internal.AMQP_STORAGE_CONTEXT
import net.corda.serialization.internal.SerializationFactoryImpl


class SerializationEngine {
    companion object {
        fun init() {
            synchronized(this) {
                if (nodeSerializationEnv == null) {
                    val classloader = this.javaClass.classLoader
                    nodeSerializationEnv = SerializationEnvironmentImpl(
                            SerializationFactoryImpl().apply {
                                registerScheme(AMQPServerSerializationScheme(emptyList()))
                            },
                            p2pContext = AMQP_P2P_CONTEXT.withClassLoader(classloader),
                            rpcServerContext = AMQP_P2P_CONTEXT.withClassLoader(classloader),
                            storageContext = AMQP_STORAGE_CONTEXT.withClassLoader(classloader),
                            checkpointContext = AMQP_P2P_CONTEXT.withClassLoader(classloader)
                    )
                }
            }
        }
    }
}