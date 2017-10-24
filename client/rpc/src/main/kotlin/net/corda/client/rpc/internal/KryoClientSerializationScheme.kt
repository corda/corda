package net.corda.client.rpc.internal

import com.esotericsoftware.kryo.pool.KryoPool
import net.corda.core.serialization.SerializationContext
import net.corda.core.utilities.ByteSequence
import net.corda.nodeapi.internal.serialization.*

class KryoClientSerializationScheme : AbstractKryoSerializationScheme() {
    override fun canDeserializeVersion(byteSequence: ByteSequence, target: SerializationContext.UseCase): Boolean {
        return byteSequence == KryoHeaderV0_1 && (target == SerializationContext.UseCase.RPCClient || target == SerializationContext.UseCase.P2P)
    }

    override fun rpcClientKryoPool(context: SerializationContext): KryoPool {
        return KryoPool.Builder {
            DefaultKryoCustomizer.customize(RPCKryo(RpcClientObservableSerializer, context)).apply {
                classLoader = context.deserializationClassLoader
            }
        }.build()
    }

    // We're on the client and don't have access to server classes.
    override fun rpcServerKryoPool(context: SerializationContext): KryoPool = throw UnsupportedOperationException()
}