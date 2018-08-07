package net.corda.node.serialization.amqp

import net.corda.core.cordapp.Cordapp
import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.amqp.AbstractAMQPSerializationScheme
import net.corda.serialization.internal.amqp.AccessOrderLinkedHashMap
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.RxNotificationSerializer
import java.util.concurrent.ConcurrentHashMap

/**
 * When set as the serialization scheme, defines the RPC Server serialization scheme as using the Corda
 * AMQP implementation.
 */
class AMQPServerSerializationScheme(
        cordappCustomSerializers: Set<SerializationCustomSerializer<*, *>>,
        serializerFactoriesForContexts: AccessOrderLinkedHashMap<Pair<ClassWhitelist, ClassLoader>, SerializerFactory>
) : AbstractAMQPSerializationScheme(cordappCustomSerializers, serializerFactoriesForContexts) {
    constructor(cordapps: List<Cordapp>) : this(cordapps.customSerializers, AccessOrderLinkedHashMap { 128 })

    constructor() : this(emptySet(), AccessOrderLinkedHashMap { 128 })

    override fun rpcClientSerializerFactory(context: SerializationContext): SerializerFactory {
        throw UnsupportedOperationException()
    }

    override fun rpcServerSerializerFactory(context: SerializationContext): SerializerFactory {
        return SerializerFactory(context.whitelist, context.deserializationClassLoader, context.lenientCarpenterEnabled).apply {
            register(RpcServerObservableSerializer())
            register(RpcServerCordaFutureSerializer(this))
            register(RxNotificationSerializer(this))
        }
    }

    override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean {
        return canDeserializeVersion(magic) &&
                (   target == SerializationContext.UseCase.P2P
                 || target == SerializationContext.UseCase.Storage
                 || target == SerializationContext.UseCase.RPCServer)
    }
}
