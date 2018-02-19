package net.corda.node.internal.serialization.testutils

import net.corda.client.rpc.internal.serialization.amqp.RpcClientObservableSerializer
import net.corda.core.context.Trace
import net.corda.core.serialization.SerializationContext
import net.corda.node.serialization.amqp.RpcServerObservableSerializer
import net.corda.nodeapi.RPCApi
import net.corda.nodeapi.internal.serialization.AllWhitelist
import net.corda.nodeapi.internal.serialization.CordaSerializationMagic
import net.corda.nodeapi.internal.serialization.amqp.AbstractAMQPSerializationScheme
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory
import net.corda.client.rpc.internal.ObservableContext as ClientObservableContext

/**
 *
 */
class AMQPTestSerializationScheme : AbstractAMQPSerializationScheme(emptyList(), TestSerializerFactoryFactory()) {
    override fun rpcClientSerializerFactory(context: SerializationContext): SerializerFactory {
        throw UnsupportedOperationException()
    }

    override fun rpcServerSerializerFactory(context: SerializationContext): SerializerFactory {
        return SerializerFactory(cl = javaClass.classLoader, whitelist = AllWhitelist).apply {
            register(RpcServerObservableSerializer(this@AMQPTestSerializationScheme))
        }
    }

    override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase) = true
}

/**
 * Special serialization context for the round trip tests that allows for both server and client RPC
 * operations
 */
class AMQPRoundTripRPCSerializationScheme(
        private val serializationContext: SerializationContext
) : AbstractAMQPSerializationScheme(emptyList(), TestSerializerFactoryFactory()) {

    override fun rpcClientSerializerFactory(context: SerializationContext): SerializerFactory {
        return SerializerFactory(cl = javaClass.classLoader, whitelist = AllWhitelist).apply {
            register(RpcClientObservableSerializer(
                    context))
        }
    }

    override fun rpcServerSerializerFactory(context: SerializationContext): SerializerFactory {
        return SerializerFactory(cl = javaClass.classLoader, whitelist = AllWhitelist).apply {
            register(RpcServerObservableSerializer(
                    this@AMQPRoundTripRPCSerializationScheme,
                    context))
        }
    }

    override fun canDeserializeVersion(
            magic: CordaSerializationMagic,
            target: SerializationContext.UseCase) = true

    fun rpcClientSerializerFactory(observableContext: ClientObservableContext, id: Trace.InvocationId) =
            rpcClientSerializerFactory(
                    RpcClientObservableSerializer.createContext(observableContext, serializationContext)
                        .withProperty(RPCApi.RpcRequestOrObservableIdKey, id)
            )


    fun rpcServerSerializerFactory(observableContext: TestObservableContext) =
            rpcServerSerializerFactory(
                    RpcServerObservableSerializer.createContext(observableContext, serializationContext))

}