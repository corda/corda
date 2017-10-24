package net.corda.core.serialization.internal

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization._contextSerializationEnv

interface SerializationEnvironment {
    val SERIALIZATION_FACTORY: SerializationFactory
    val P2P_CONTEXT: SerializationContext
    val RPC_SERVER_CONTEXT: SerializationContext
    val RPC_CLIENT_CONTEXT: SerializationContext
    val STORAGE_CONTEXT: SerializationContext
    val CHECKPOINT_CONTEXT: SerializationContext
    /** Use this to set the env if [callable] doesn't spawn threads (that do serialization). */
    fun <T> asContextEnv(callable: () -> T) = run {
        _contextSerializationEnv.set(this)
        try {
            callable()
        } finally {
            _contextSerializationEnv.set(null)
        }
    }
}
