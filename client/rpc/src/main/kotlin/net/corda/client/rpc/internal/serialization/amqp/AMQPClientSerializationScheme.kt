package net.corda.client.rpc.internal.serialization.amqp

import net.corda.core.cordapp.Cordapp
import net.corda.core.serialization.*
import net.corda.core.serialization.SerializationContext.*
import net.corda.core.serialization.internal.*
import net.corda.serialization.internal.*
import net.corda.serialization.internal.amqp.AbstractAMQPSerializationScheme
import net.corda.serialization.internal.amqp.AccessOrderLinkedHashMap
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.amqpMagic
import net.corda.serialization.internal.amqp.custom.RxNotificationSerializer

/**
 * When set as the serialization scheme for a process, sets it to be the Corda AMQP implementation.
 * This scheme is for use by the RPC Client calls.
 */
class AMQPClientSerializationScheme(
            cordappCustomSerializers: Set<SerializationCustomSerializer<*,*>>,
            serializerFactoriesForContexts: AccessOrderLinkedHashMap<Pair<ClassWhitelist, ClassLoader>, SerializerFactory>
    ) : AbstractAMQPSerializationScheme(cordappCustomSerializers, serializerFactoriesForContexts) {
    constructor(cordapps: List<Cordapp>) : this(cordapps.customSerializers, AccessOrderLinkedHashMap { 128 })

    @Suppress("UNUSED")
    constructor() : this(emptySet(), AccessOrderLinkedHashMap { 128 })

    companion object {
        /** Call from main only. */
        fun initialiseSerialization(classLoader: ClassLoader? = null) {
            nodeSerializationEnv = createSerializationEnv(classLoader)
        }

        fun createSerializationEnv(classLoader: ClassLoader? = null): SerializationEnvironment {
            return SerializationEnvironmentImpl(
                    amqp = AMQPSerializationEnvironment(
                        SerializationFactoryImpl().apply {
                            registerScheme(AMQPClientSerializationScheme(emptyList()))
                        },
                        storageContext = AMQP_STORAGE_CONTEXT,
                        p2pContext = if (classLoader != null) AMQP_P2P_CONTEXT.withClassLoader(classLoader)
                            else AMQP_P2P_CONTEXT,
                        rpc = RPCSerializationEnvironment(
                                clientContext = AMQP_RPC_CLIENT_CONTEXT,
                                serverContext = AMQP_RPC_SERVER_CONTEXT)))
        }
    }

    override fun canDeserializeVersion(target: UseCase): Boolean {
        return target == UseCase.RPCClient || target == UseCase.P2P
    }

    override fun rpcClientSerializerFactory(context: SerializationContext): SerializerFactory {
        return SerializerFactory(context.whitelist, context.deserializationClassLoader, context.lenientCarpenterEnabled).apply {
            register(RpcClientObservableDeSerializer)
            register(RpcClientCordaFutureSerializer(this))
            register(RxNotificationSerializer(this))
        }
    }

    override fun rpcServerSerializerFactory(context: SerializationContext): SerializerFactory {
        throw UnsupportedOperationException()
    }
}
