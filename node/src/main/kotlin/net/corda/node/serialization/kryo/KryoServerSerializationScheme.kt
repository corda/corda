package net.corda.node.serialization.kryo

import com.esotericsoftware.kryo.pool.KryoPool
import net.corda.core.serialization.SerializationContext
import net.corda.serialization.internal.CordaSerializationMagic

class KryoServerSerializationScheme : AbstractKryoSerializationScheme() {
    override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean {
        return magic == kryoMagic && target == SerializationContext.UseCase.Checkpoint
    }

    override fun rpcClientKryoPool(context: SerializationContext): KryoPool = throw UnsupportedOperationException()
    override fun rpcServerKryoPool(context: SerializationContext): KryoPool = throw UnsupportedOperationException()
}
