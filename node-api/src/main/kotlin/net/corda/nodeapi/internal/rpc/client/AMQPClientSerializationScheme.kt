package net.corda.nodeapi.internal.rpc.client

import net.corda.core.cordapp.Cordapp
import net.corda.core.internal.toSynchronised
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationContext.UseCase
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal._rpcClientSerializationEnv
import net.corda.serialization.internal.AMQP_P2P_CONTEXT
import net.corda.serialization.internal.AMQP_RPC_CLIENT_CONTEXT
import net.corda.serialization.internal.AMQP_RPC_SERVER_CONTEXT
import net.corda.serialization.internal.AMQP_STORAGE_CONTEXT
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.SerializationFactoryImpl
import net.corda.serialization.internal.amqp.AbstractAMQPSerializationScheme
import net.corda.serialization.internal.amqp.AccessOrderLinkedHashMap
import net.corda.serialization.internal.amqp.SerializationFactoryCacheKey
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.SerializerFactoryBuilder
import net.corda.serialization.internal.amqp.amqpMagic
import net.corda.serialization.internal.amqp.custom.RxNotificationSerializer

/**
 * When set as the serialization scheme for a process, sets it to be the Corda AMQP implementation.
 * This scheme is for use by the RPC Client calls.
 */
class AMQPClientSerializationScheme(
        cordappCustomSerializers: Set<SerializationCustomSerializer<*,*>>,
        cordappSerializationWhitelists: Set<SerializationWhitelist>,
        serializerFactoriesForContexts: MutableMap<SerializationFactoryCacheKey, SerializerFactory>
    ) : AbstractAMQPSerializationScheme(cordappCustomSerializers, cordappSerializationWhitelists, serializerFactoriesForContexts) {
    constructor(cordapps: List<Cordapp>) : this(cordapps.customSerializers, cordapps.serializationWhitelists)
    constructor(cordapps: List<Cordapp>, serializerFactoriesForContexts: MutableMap<SerializationFactoryCacheKey, SerializerFactory>)
        : this(cordapps.customSerializers, cordapps.serializationWhitelists, serializerFactoriesForContexts)
    constructor(
        cordappCustomSerializers: Set<SerializationCustomSerializer<*,*>>,
        cordappSerializationWhitelists: Set<SerializationWhitelist>
    ) : this(cordappCustomSerializers, cordappSerializationWhitelists, createDefaultSerializerFactoryCache())

    @Suppress("UNUSED")
    constructor() : this(emptySet(), emptySet())

    companion object {
        /** Call from main only. */
        fun initialiseSerialization(classLoader: ClassLoader? = null, customSerializers: Set<SerializationCustomSerializer<*, *>> = emptySet(), serializationWhitelists: Set<SerializationWhitelist> = emptySet(), serializerFactoriesForContexts: MutableMap<SerializationFactoryCacheKey, SerializerFactory> = AccessOrderLinkedHashMap<SerializationFactoryCacheKey, SerializerFactory>(128).toSynchronised()) {
            _rpcClientSerializationEnv.set(createSerializationEnv(classLoader, customSerializers,
                    serializationWhitelists, serializerFactoriesForContexts))
        }

        fun createSerializationEnv(classLoader: ClassLoader? = null, customSerializers: Set<SerializationCustomSerializer<*, *>> = emptySet(), serializationWhitelists: Set<SerializationWhitelist> = emptySet(), serializerFactoriesForContexts: MutableMap<SerializationFactoryCacheKey, SerializerFactory> = AccessOrderLinkedHashMap<SerializationFactoryCacheKey, SerializerFactory>(128).toSynchronised()): SerializationEnvironment {
            return SerializationEnvironment.with(
                    SerializationFactoryImpl().apply {
                        registerScheme(AMQPClientSerializationScheme(customSerializers, serializationWhitelists, serializerFactoriesForContexts))
                    },
                    storageContext = AMQP_STORAGE_CONTEXT,
                    p2pContext = if (classLoader != null) AMQP_P2P_CONTEXT.withClassLoader(classLoader) else AMQP_P2P_CONTEXT,
                    rpcClientContext = AMQP_RPC_CLIENT_CONTEXT,
                    rpcServerContext = AMQP_RPC_SERVER_CONTEXT
            )
        }

        private fun createDefaultSerializerFactoryCache(): MutableMap<SerializationFactoryCacheKey, SerializerFactory> {
            return AccessOrderLinkedHashMap<SerializationFactoryCacheKey, SerializerFactory>(128).toSynchronised()
        }
    }

    override fun canDeserializeVersion(magic: CordaSerializationMagic, target: UseCase): Boolean {
        return magic == amqpMagic && (target == UseCase.RPCClient || target == UseCase.P2P)
    }

    override fun rpcClientSerializerFactory(context: SerializationContext): SerializerFactory {
        return SerializerFactoryBuilder.build(context.whitelist, context.deserializationClassLoader, context.lenientCarpenterEnabled).apply {
            register(RpcClientObservableDeSerializer)
            register(RpcClientCordaFutureSerializer(this))
            register(RxNotificationSerializer(this))
        }
    }

    override fun rpcServerSerializerFactory(context: SerializationContext): SerializerFactory {
        throw UnsupportedOperationException()
    }
}
