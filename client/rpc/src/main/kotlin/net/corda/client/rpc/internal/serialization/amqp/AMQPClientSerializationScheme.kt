package net.corda.client.rpc.internal.serialization.amqp

import net.corda.core.cordapp.Cordapp
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.nodeapi.internal.serialization.*
import net.corda.nodeapi.internal.serialization.amqp.AbstractAMQPSerializationScheme
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory
import net.corda.nodeapi.internal.serialization.amqp.amqpMagic
import net.corda.nodeapi.internal.serialization.amqp.custom.RXNotificationSerializer

class AMQPClientSerializationScheme (corDapps: List<Cordapp>) : AbstractAMQPSerializationScheme(corDapps) {
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
        magic == amqpMagic && target == SerializationContext.UseCase.RPCClient

    override fun rpcClientSerializerFactory(context: SerializationContext): SerializerFactory {
        return SerializerFactory(context.whitelist, ClassLoader.getSystemClassLoader()).apply {
            register(RpcClientObservableSerializer)
            register(RpcClientCordaFutureSerializer(this))
            register(RXNotificationSerializer(this))
        }
    }

    override fun rpcServerSerializerFactory(context: SerializationContext): SerializerFactory {
        throw UnsupportedOperationException()
    }
}