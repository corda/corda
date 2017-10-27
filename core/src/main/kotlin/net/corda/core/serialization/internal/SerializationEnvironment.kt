package net.corda.core.serialization.internal

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationFactory

interface SerializationEnvironment {
    val SERIALIZATION_FACTORY: SerializationFactory
    val P2P_CONTEXT: SerializationContext
    val RPC_SERVER_CONTEXT: SerializationContext
    val RPC_CLIENT_CONTEXT: SerializationContext
    val STORAGE_CONTEXT: SerializationContext
    val CHECKPOINT_CONTEXT: SerializationContext
}
