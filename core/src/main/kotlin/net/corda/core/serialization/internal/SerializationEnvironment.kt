/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.serialization.internal

import net.corda.core.internal.InheritableThreadLocalToggleField
import net.corda.core.internal.SimpleToggleField
import net.corda.core.internal.ThreadLocalToggleField
import net.corda.core.internal.VisibleForTesting
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationFactory

interface SerializationEnvironment {
    val serializationFactory: SerializationFactory
    val p2pContext: SerializationContext
    val rpcServerContext: SerializationContext
    val rpcClientContext: SerializationContext
    val storageContext: SerializationContext
    val checkpointContext: SerializationContext
}

open class SerializationEnvironmentImpl(
        override val serializationFactory: SerializationFactory,
        override val p2pContext: SerializationContext,
        rpcServerContext: SerializationContext? = null,
        rpcClientContext: SerializationContext? = null,
        storageContext: SerializationContext? = null,
        checkpointContext: SerializationContext? = null) : SerializationEnvironment {
    // Those that are passed in as null are never inited:
    override lateinit var rpcServerContext: SerializationContext
    override lateinit var rpcClientContext: SerializationContext
    override lateinit var storageContext: SerializationContext
    override lateinit var checkpointContext: SerializationContext

    init {
        rpcServerContext?.let { this.rpcServerContext = it }
        rpcClientContext?.let { this.rpcClientContext = it }
        storageContext?.let { this.storageContext = it }
        checkpointContext?.let { this.checkpointContext = it }
    }
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
