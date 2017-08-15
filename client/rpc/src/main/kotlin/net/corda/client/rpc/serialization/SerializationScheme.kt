package net.corda.client.rpc.serialization

import com.esotericsoftware.kryo.pool.KryoPool
import net.corda.client.rpc.internal.RpcClientObservableSerializer
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationFactory
import net.corda.core.utilities.ByteSequence
import net.corda.nodeapi.RPCKryo
import net.corda.nodeapi.internal.serialization.AbstractKryoSerializationScheme
import net.corda.nodeapi.internal.serialization.DefaultKryoCustomizer
import net.corda.nodeapi.internal.serialization.KryoHeaderV0_1

class KryoClientSerializationScheme(serializationFactory: SerializationFactory) : AbstractKryoSerializationScheme(serializationFactory) {
    override fun canDeserializeVersion(byteSequence: ByteSequence, target: SerializationContext.UseCase): Boolean {
        return byteSequence == KryoHeaderV0_1 && (target == SerializationContext.UseCase.RPCClient || target == SerializationContext.UseCase.P2P)
    }

    override fun rpcClientKryoPool(context: SerializationContext): KryoPool {
        return KryoPool.Builder {
            DefaultKryoCustomizer.customize(RPCKryo(RpcClientObservableSerializer, serializationFactory, context)).apply { classLoader = context.deserializationClassLoader }
        }.build()
    }

    // We're on the client and don't have access to server classes.
    override fun rpcServerKryoPool(context: SerializationContext): KryoPool {
        throw UnsupportedOperationException()
    }
}