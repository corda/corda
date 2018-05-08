package net.corda.node.serialization.kryo

import com.esotericsoftware.kryo.pool.KryoPool
import net.corda.core.serialization.SerializationContext
import net.corda.nodeapi.internal.serialization.CordaSerializationMagic
import net.corda.nodeapi.internal.serialization.kryo.AbstractKryoSerializationScheme
import net.corda.nodeapi.internal.serialization.kryo.DefaultKryoCustomizer
import net.corda.nodeapi.internal.serialization.kryo.kryoMagic
import net.corda.nodeapi.internal.serialization.kryo.RPCKryo

class KryoServerSerializationScheme : AbstractKryoSerializationScheme() {
    override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean {
        return magic == kryoMagic && target != SerializationContext.UseCase.RPCClient
    }

    override fun rpcClientKryoPool(context: SerializationContext): KryoPool = throw UnsupportedOperationException()

    override fun rpcServerKryoPool(context: SerializationContext): KryoPool {
        return KryoPool.Builder {
            DefaultKryoCustomizer.customize(RPCKryo(RpcServerObservableSerializer, context), publicKeySerializer).apply {
                classLoader = context.deserializationClassLoader
            }
        }.build()
    }
}