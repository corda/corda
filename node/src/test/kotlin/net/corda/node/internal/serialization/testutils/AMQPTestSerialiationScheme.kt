package net.corda.node.internal.serialization.testutils

import net.corda.client.rpc.internal.serialization.amqp.RpcClientObservableSerializer
import net.corda.core.context.Trace
import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.node.serialization.amqp.RpcServerObservableSerializer
import net.corda.nodeapi.RPCApi
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.amqp.AbstractAMQPSerializationScheme
import net.corda.serialization.internal.amqp.SerializerFactory
import java.util.concurrent.ConcurrentMap
import net.corda.client.rpc.internal.ObservableContext as ClientObservableContext

/**
 * Special serialization context for the round trip tests that allows for both server and client RPC
 * operations
 */


class AMQPRoundTripRPCSerializationScheme(
        private val serializationContext: SerializationContext,
        cordappCustomSerializers: Set<SerializationCustomSerializer<*, *>>,
        serializerFactoriesForContexts: ConcurrentMap<Pair<ClassWhitelist, ClassLoader>, SerializerFactory>)
    : AbstractAMQPSerializationScheme(
        cordappCustomSerializers, serializerFactoriesForContexts
) {
    override fun rpcClientSerializerFactory(context: SerializationContext): SerializerFactory {
        return SerializerFactory(AllWhitelist, javaClass.classLoader).apply {
            register(RpcClientObservableSerializer)
        }
    }

    override fun rpcServerSerializerFactory(context: SerializationContext): SerializerFactory {
        return SerializerFactory(AllWhitelist, javaClass.classLoader).apply {
            register(RpcServerObservableSerializer())
        }
    }

    override fun canDeserializeVersion(
            magic: CordaSerializationMagic,
            target: SerializationContext.UseCase) = true

    fun rpcClientSerializerFactory(observableContext: ClientObservableContext, id: Trace.InvocationId) =
            rpcClientSerializerFactory(
                    RpcClientObservableSerializer.createContext(serializationContext, observableContext)
                        .withProperty(RPCApi.RpcRequestOrObservableIdKey, id))

    fun rpcServerSerializerFactory(observableContext: TestObservableContext) =
            rpcServerSerializerFactory(
                    RpcServerObservableSerializer.createContext(serializationContext, observableContext))
}