@file:KeepForDJVM
package net.corda.core.serialization.internal

import net.corda.core.KeepForDJVM
import net.corda.core.internal.*
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationFactory

@KeepForDJVM
interface SerializationEnvironment {

    companion object {
        @JvmOverloads
        fun with(nonCheckpoint: NonCheckpointEnvironment, checkpoint: CheckpointEnvironment? = null) =
                ConfiguredSerializationEnvironment(nonCheckpoint, checkpoint)
    }

    val serializationFactory: SerializationFactory
    val checkpointSerializer: CheckpointSerializer
    val p2pContext: SerializationContext
    val rpcServerContext: SerializationContext
    val rpcClientContext: SerializationContext
    val storageContext: SerializationContext
    val checkpointContext: CheckpointSerializationContext
}

data class ConfiguredSerializationEnvironment(
        val nonCheckpoint: NonCheckpointEnvironment,
        val checkpoint: CheckpointEnvironment? = null): SerializationEnvironment {

    override val serializationFactory: SerializationFactory get() = nonCheckpoint.factory
    override val checkpointSerializer: CheckpointSerializer get() = checkpoint?.serializer ?:
        throw IllegalStateException("Checkpoint serializer not configured in this environment")
    override val p2pContext: SerializationContext get() = nonCheckpoint.contexts.p2p ?:
        throw IllegalStateException("P2P serialization context not configured in this environment")
    override val rpcServerContext: SerializationContext get() = nonCheckpoint.contexts.rpc?.server ?:
        throw IllegalStateException("RPC server serialization context not configured in this environment")
    override val rpcClientContext: SerializationContext get() = nonCheckpoint.contexts.rpc?.client ?:
        throw IllegalStateException("RPC client serialization context not configured in this environment")
    override val storageContext: SerializationContext get() = nonCheckpoint.contexts.storage ?:
        throw IllegalStateException("Storage serialization context not configured in this environment")
    override val checkpointContext: CheckpointSerializationContext get() = checkpoint?.context ?:
        throw IllegalStateException("Checkpoint serialization context not configured in this environment")
}

data class NonCheckpointEnvironment(val factory: SerializationFactory, val contexts: SerializationContexts)
data class SerializationContexts @JvmOverloads constructor(val p2p: SerializationContext? = null, val rpc: RPCSerializationContexts? = null, val storage: SerializationContext? = null)
data class RPCSerializationContexts @JvmOverloads constructor(val server: SerializationContext, val client: SerializationContext? = null)
data class CheckpointEnvironment(val serializer: CheckpointSerializer, val context: CheckpointSerializationContext)

private val _nodeSerializationEnv = SimpleToggleField<SerializationEnvironment>("nodeSerializationEnv", true)
@VisibleForTesting
val _globalSerializationEnv = SimpleToggleField<SerializationEnvironment>("globalSerializationEnv")
@VisibleForTesting
val _contextSerializationEnv = ThreadLocalToggleField<SerializationEnvironment>("contextSerializationEnv")
@VisibleForTesting
val _inheritableContextSerializationEnv = InheritableThreadLocalToggleField<SerializationEnvironment>("inheritableContextSerializationEnv") { stack ->
    stack.fold(false) { isAGlobalThreadBeingCreated, e ->
        isAGlobalThreadBeingCreated ||
                (e.className == "io.netty.util.concurrent.GlobalEventExecutor" && e.methodName == "startThread") ||
                (e.className == "java.util.concurrent.ForkJoinPool\$DefaultForkJoinWorkerThreadFactory" && e.methodName == "newThread")
    }
}
private val serializationEnvProperties = listOf(_nodeSerializationEnv, _globalSerializationEnv, _contextSerializationEnv, _inheritableContextSerializationEnv)
val effectiveSerializationEnv: SerializationEnvironment
    get() = serializationEnvProperties.map { Pair(it, it.get()) }.filter { it.second != null }.run {
        singleOrNull()?.run {
            second!!
        } ?: throw IllegalStateException("Expected exactly 1 of {${serializationEnvProperties.joinToString(", ") { it.name }}} but got: {${joinToString(", ") { it.first.name }}}")
    }
/** Should be set once in main. */
var nodeSerializationEnv by _nodeSerializationEnv