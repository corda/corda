package net.corda.testing.internal

import net.corda.client.rpc.internal.serialization.amqp.AMQPClientSerializationScheme
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.internal._inheritableContextSerializationEnv
import net.corda.node.serialization.amqp.AMQPServerSerializationScheme
import net.corda.node.serialization.kryo.KRYO_CHECKPOINT_CONTEXT
import net.corda.node.serialization.kryo.KryoCheckpointSerializer
import net.corda.serialization.internal.*
import net.corda.testing.common.internal.asContextEnv
import net.corda.testing.core.SerializationEnvironmentRule
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

val inVMExecutors = ConcurrentHashMap<SerializationEnvironment, ExecutorService>()

/**
 * For example your test class uses [SerializationEnvironmentRule] but you want to turn it off for one method.
 * Use sparingly, ideally a test class shouldn't mix serializers init mechanisms.
 */
fun <T> withoutTestSerialization(callable: () -> T): T { // TODO: Delete this, see CORDA-858.
    val (property, env) = listOf(_contextSerializationEnv, _inheritableContextSerializationEnv).map { Pair(it, it.get()) }.single { it.second != null }
    property.set(null)
    try {
        return callable()
    } finally {
        property.set(env)
    }
}

fun createTestSerializationEnv(): SerializationEnvironment {
    val factory = SerializationFactoryImpl().apply {
        registerScheme(AMQPClientSerializationScheme(emptyList()))
        registerScheme(AMQPServerSerializationScheme(emptyList()))
    }
    return SerializationEnvironment.with(
            factory,
            AMQP_P2P_CONTEXT,
            AMQP_RPC_SERVER_CONTEXT,
            AMQP_RPC_CLIENT_CONTEXT,
            AMQP_STORAGE_CONTEXT,
            KRYO_CHECKPOINT_CONTEXT,
            KryoCheckpointSerializer
    )
}

fun <T> SerializationEnvironment.asTestContextEnv(inheritable: Boolean = false, callable: (SerializationEnvironment) -> T): T {
    try {
        return asContextEnv(inheritable, callable)
    } finally {
        inVMExecutors.remove(this)
    }
}
