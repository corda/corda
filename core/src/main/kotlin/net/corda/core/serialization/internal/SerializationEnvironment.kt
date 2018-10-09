@file:KeepForDJVM
package net.corda.core.serialization.internal

import net.corda.core.KeepForDJVM
import net.corda.core.internal.InheritableThreadLocalToggleField
import net.corda.core.internal.SimpleToggleField
import net.corda.core.internal.ThreadLocalToggleField
import net.corda.core.internal.VisibleForTesting
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationFactory

@KeepForDJVM
interface SerializationEnvironment {

    companion object {
        fun with(
                serializationFactory: SerializationFactory,
                p2pContext: SerializationContext,
                rpcServerContext: SerializationContext? = null,
                rpcClientContext: SerializationContext? = null,
                storageContext: SerializationContext? = null,

                checkpointContext: CheckpointSerializationContext? = null,
                checkpointSerializer: CheckpointSerializer? = null
        ): SerializationEnvironment =
                SerializationEnvironmentImpl(
                        serializationFactory = serializationFactory,
                        p2pContext = p2pContext,
                        optionalRpcServerContext = rpcServerContext,
                        optionalRpcClientContext = rpcClientContext,
                        optionalStorageContext = storageContext,
                        optionalCheckpointContext = checkpointContext,
                        optionalCheckpointSerializer = checkpointSerializer
                )
    }

    val serializationFactory: SerializationFactory
    val p2pContext: SerializationContext
    val rpcServerContext: SerializationContext
    val rpcClientContext: SerializationContext
    val storageContext: SerializationContext

    val checkpointSerializer: CheckpointSerializer
    val checkpointContext: CheckpointSerializationContext
}

@KeepForDJVM
private class SerializationEnvironmentImpl(
        override val serializationFactory: SerializationFactory,
        override val p2pContext: SerializationContext,
        private val optionalRpcServerContext: SerializationContext? = null,
        private val optionalRpcClientContext: SerializationContext? = null,
        private val optionalStorageContext: SerializationContext? = null,
        private val optionalCheckpointContext: CheckpointSerializationContext? = null,
        private val optionalCheckpointSerializer: CheckpointSerializer? = null) : SerializationEnvironment {

    override val rpcServerContext: SerializationContext get() = optionalRpcServerContext ?:
            throw UnsupportedOperationException("RPC server serialization not supported in this environment")

    override val rpcClientContext: SerializationContext get() = optionalRpcClientContext ?:
        throw UnsupportedOperationException("RPC client serialization not supported in this environment")

    override val storageContext: SerializationContext get() = optionalStorageContext ?:
        throw UnsupportedOperationException("Storage serialization not supported in this environment")

    override val checkpointContext: CheckpointSerializationContext get() = optionalCheckpointContext ?:
        throw UnsupportedOperationException("Checkpoint serialization not supported in this environment")

    override val checkpointSerializer: CheckpointSerializer get() = optionalCheckpointSerializer ?:
        throw UnsupportedOperationException("Checkpoint serialization not supported in this environment")
}

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
