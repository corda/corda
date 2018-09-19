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
    val serializationFactory: SerializationFactory
    val checkpointSerializer: CheckpointSerializer
    val p2pContext: SerializationContext
    val rpcServerContext: SerializationContext
    val rpcClientContext: SerializationContext
    val storageContext: SerializationContext
    val checkpointContext: CheckpointSerializationContext
}

@KeepForDJVM
data class AMQPSerializationEnvironment(
    val serializationFactory: SerializationFactory,
    val p2pContext: SerializationContext,
    val rpc: RPCSerializationEnvironment? = null,
    val storageContext: SerializationContext? = null)

@KeepForDJVM
data class RPCSerializationEnvironment(
        val serverContext: SerializationContext,
        val clientContext: SerializationContext? = null)

@KeepForDJVM
data class CheckpointSerializationEnvironment (
    val checkpointSerializer: CheckpointSerializer,
    val checkpointContext : CheckpointSerializationContext
)

@KeepForDJVM
open class SerializationEnvironmentImpl(
        private val amqp: AMQPSerializationEnvironment? = null,
        private val checkpoint: CheckpointSerializationEnvironment? = null) : SerializationEnvironment {

    private val amqpIfSupported get() = amqp ?:
        throw UnsupportedOperationException("AMQP serialization not supported in this environment")

    private val checkpointIfSupported get() = checkpoint ?:
        throw UnsupportedOperationException("Checkpoint serialization not supported in this environment")

    private val rpcIfSupported get() = amqpIfSupported.rpc ?:
        throw UnsupportedOperationException("RPC serialization not supported in this environment")

    override val checkpointSerializer get() = checkpointIfSupported.checkpointSerializer
    override val checkpointContext get() = checkpointIfSupported.checkpointContext

    override val serializationFactory get() = amqpIfSupported.serializationFactory
    override val p2pContext get() = amqpIfSupported.p2pContext
    override val rpcServerContext get() = rpcIfSupported.serverContext
    override val rpcClientContext get() = rpcIfSupported.clientContext ?:
            throw UnsupportedOperationException("RPC client serialization not supported in this environment")

    override val storageContext get() = amqpIfSupported.storageContext ?:
        throw UnsupportedOperationException("Storage serialization not supported in this environment")
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
