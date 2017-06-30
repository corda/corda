package net.corda.node.serialization

import com.esotericsoftware.kryo.pool.KryoPool
import net.corda.core.serialization.DefaultKryoCustomizer
import net.corda.core.serialization.SerializationContext
import net.corda.core.utilities.ByteSequence
import net.corda.node.services.messaging.RpcServerObservableSerializer
import net.corda.nodeapi.RPCKryo
import net.corda.nodeapi.serialization.AbstractKryoSerializationScheme
import net.corda.nodeapi.serialization.KryoHeaderV0_1

class KryoServerSerializationScheme : AbstractKryoSerializationScheme() {
    override fun canDeserializeVersion(byteSequence: ByteSequence, target: SerializationContext.UseCase): Boolean {
        return byteSequence.equals(KryoHeaderV0_1) && target != SerializationContext.UseCase.RPCClient
    }

    override fun rpcClientKryoPool(context: SerializationContext): KryoPool {
        throw UnsupportedOperationException()
    }

    override fun rpcServerKryoPool(context: SerializationContext): KryoPool {
        return KryoPool.Builder {
            DefaultKryoCustomizer.customize(RPCKryo(RpcServerObservableSerializer, context.whitelist)).apply { classLoader = context.deserializationClassLoader }
        }.build()
    }
}