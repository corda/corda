package net.corda.bridge.internal

import net.corda.core.cordapp.Cordapp
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.SerializationWhitelist
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.amqp.*

class AMQPFirewallSerializationScheme(
        cordappCustomSerializers: Set<SerializationCustomSerializer<*, *>>,
        cordappSerializationWhitelists: Set<SerializationWhitelist>,
        serializerFactoriesForContexts: AccessOrderLinkedHashMap<SerializationFactoryCacheKey, SerializerFactory>
) : AbstractAMQPSerializationScheme(cordappCustomSerializers, cordappSerializationWhitelists, serializerFactoriesForContexts) {
    constructor(cordapps: List<Cordapp>) : this(cordapps.customSerializers, cordapps.serializationWhitelists, AccessOrderLinkedHashMap { 128 })

    override fun rpcClientSerializerFactory(context: SerializationContext): SerializerFactory {
        throw UnsupportedOperationException()
    }

    override fun rpcServerSerializerFactory(context: SerializationContext): SerializerFactory {
        throw UnsupportedOperationException()
    }

    override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase) = (magic == amqpMagic && target == SerializationContext.UseCase.P2P)
}