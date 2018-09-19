package net.corda.bootstrapper.serialization

import net.corda.core.serialization.internal.*
import net.corda.node.serialization.amqp.AMQPServerSerializationScheme
import net.corda.node.serialization.kryo.KRYO_CHECKPOINT_CONTEXT
<<<<<<< HEAD
import net.corda.node.serialization.kryo.KryoCheckpointSerializer
import net.corda.serialization.internal.*
=======
import net.corda.serialization.internal.AMQP_P2P_CONTEXT
import net.corda.serialization.internal.AMQP_STORAGE_CONTEXT
import net.corda.serialization.internal.SerializationFactoryImpl
>>>>>>> master

class SerializationEngine {
    companion object {
        fun init() {
            synchronized(this) {
                if (nodeSerializationEnv == null) {
                    val classloader = this::class.java.classLoader
                    nodeSerializationEnv = SerializationEnvironmentImpl(
<<<<<<< HEAD
                            checkpoint = CheckpointSerializationEnvironment(
                                    KryoCheckpointSerializer,
                                    KRYO_CHECKPOINT_CONTEXT.withClassLoader(classloader)
                            ),
                            amqp = AMQPSerializationEnvironment(
                                    SerializationFactoryImpl().apply {
                                        registerScheme(AMQPServerSerializationScheme(emptyList()))
                                    },
                                    p2pContext = AMQP_P2P_CONTEXT.withClassLoader(classloader),
                                    rpc = RPCSerializationEnvironment(
                                            serverContext = AMQP_RPC_SERVER_CONTEXT
                                    ),
                                    storageContext = AMQP_STORAGE_CONTEXT.withClassLoader(classloader)

                            ))
=======
                            SerializationFactoryImpl().apply {
                                registerScheme(AMQPServerSerializationScheme(emptyList()))
                            },
                            p2pContext = AMQP_P2P_CONTEXT.withClassLoader(classloader),
                            rpcServerContext = AMQP_P2P_CONTEXT.withClassLoader(classloader),
                            storageContext = AMQP_STORAGE_CONTEXT.withClassLoader(classloader),
                            checkpointContext = KRYO_CHECKPOINT_CONTEXT.withClassLoader(classloader)
                    )
>>>>>>> master
                }
            }
        }
    }
}
