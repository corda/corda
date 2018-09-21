package net.corda.bootstrapper.serialization

import net.corda.core.serialization.internal.*
import net.corda.node.serialization.amqp.AMQPServerSerializationScheme
import net.corda.node.serialization.kryo.KRYO_CHECKPOINT_CONTEXT
import net.corda.node.serialization.kryo.KryoCheckpointSerializer
import net.corda.serialization.internal.*

class SerializationEngine {
    companion object {
        fun init() {
            synchronized(this) {
                if (nodeSerializationEnv == null) {
                    val classloader = this::class.java.classLoader
                    nodeSerializationEnv = SerializationEnvironment.with(
                            nonCheckpoint = NonCheckpointEnvironment(
                                    factory = serializationFactory(AMQPServerSerializationScheme(emptyList())),
                                    contexts = SerializationContexts(
                                            p2p = AMQP_P2P_CONTEXT.withClassLoader(classloader),
                                            storage = AMQP_STORAGE_CONTEXT.withClassLoader(classloader),
                                            rpc = RPCSerializationContexts(
                                                    server = AMQP_RPC_SERVER_CONTEXT.withClassLoader(classloader)
                                            )
                                    )),
                            checkpoint = CheckpointEnvironment(
                                    serializer = KryoCheckpointSerializer,
                                    context = KRYO_CHECKPOINT_CONTEXT
                            ))
                }
            }
        }
    }
}
