package net.corda.client.rpc.internal.serialization.amqp

import net.corda.core.cordapp.Cordapp
import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.nodeapi.internal.serialization.*
import net.corda.nodeapi.internal.serialization.amqp.AbstractAMQPSerializationScheme
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory
import net.corda.nodeapi.internal.serialization.amqp.amqpMagic
import java.util.concurrent.ConcurrentHashMap
import net.corda.nodeapi.internal.serialization.amqp.custom.RxNotificationSerializer

/**
 * When set as the serialization scheme for a process, sets it to be the Corda AMQP implementation.
 * This scheme is for use by the RPC Client calls.
 */
class AMQPClientSerializationScheme(
            cordappCustomSerializers: Set<SerializationCustomSerializer<*,*>>,
            serializerFactoriesForContexts: MutableMap<Pair<ClassWhitelist, ClassLoader>, SerializerFactory>
    ) : AbstractAMQPSerializationScheme(cordappCustomSerializers, serializerFactoriesForContexts) {
        constructor(cordapps: List<Cordapp>) : this(cordapps.customSerializers, ConcurrentHashMap())

        @Suppress("UNUSED")
        constructor() : this(emptySet(), ConcurrentHashMap())

    companion object {
        /** Call from main only. */
        fun initialiseSerialization() {
            nodeSerializationEnv = createSerializationEnv()
        }

        fun createSerializationEnv(): SerializationEnvironment {
            return SerializationEnvironmentImpl(
                    SerializationFactoryImpl().apply {
                        registerScheme(AMQPClientSerializationScheme(emptyList()))
                    },
                    storageContext = AMQP_STORAGE_CONTEXT,
                    p2pContext = AMQP_P2P_CONTEXT,
                    rpcClientContext = AMQP_RPC_CLIENT_CONTEXT,
                    rpcServerContext = AMQP_RPC_SERVER_CONTEXT)
        }
    }

    override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase) =
        magic == amqpMagic && (
            target == SerializationContext.UseCase.RPCClient || target == SerializationContext.UseCase.P2P)

    override fun rpcClientSerializerFactory(context: SerializationContext): SerializerFactory {
        return SerializerFactory(context.whitelist, ClassLoader.getSystemClassLoader()).apply {
            register(RpcClientObservableSerializer)
            register(RpcClientCordaFutureSerializer(this))
            register(RxNotificationSerializer(this))
        }
    }

    override fun rpcServerSerializerFactory(context: SerializationContext): SerializerFactory {
        throw UnsupportedOperationException()
    }
}