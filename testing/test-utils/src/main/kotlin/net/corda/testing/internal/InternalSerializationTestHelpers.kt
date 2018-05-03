package net.corda.testing.internal

import com.nhaarman.mockito_kotlin.doNothing
import com.nhaarman.mockito_kotlin.whenever
import net.corda.client.rpc.internal.serialization.kryo.KryoClientSerializationScheme
import net.corda.core.DoNotImplement
import net.corda.core.serialization.internal.*
import net.corda.node.serialization.kryo.KryoServerSerializationScheme
import net.corda.nodeapi.internal.serialization.*
import net.corda.nodeapi.internal.serialization.amqp.AMQPClientSerializationScheme
import net.corda.nodeapi.internal.serialization.amqp.AMQPServerSerializationScheme
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

internal fun createTestSerializationEnv(label: String): SerializationEnvironmentImpl {
    val factory = SerializationFactoryImpl().apply {
        registerScheme(KryoClientSerializationScheme())
        registerScheme(KryoServerSerializationScheme())
        registerScheme(AMQPClientSerializationScheme(emptyList()))
        registerScheme(AMQPServerSerializationScheme(emptyList()))
    }
    return object : SerializationEnvironmentImpl(
            factory,
            AMQP_P2P_CONTEXT,
            KRYO_RPC_SERVER_CONTEXT,
            KRYO_RPC_CLIENT_CONTEXT,
            AMQP_STORAGE_CONTEXT,
            KRYO_CHECKPOINT_CONTEXT
    ) {
        override fun toString() = "testSerializationEnv($label)"
    }
}

/**
 * Should only be used by Driver and MockNode.
 * @param armed true to install, false to do nothing and return a dummy env.
 */
fun setGlobalSerialization(armed: Boolean): GlobalSerializationEnvironment {
    return if (armed) {
        object : GlobalSerializationEnvironment, SerializationEnvironment by createTestSerializationEnv("<global>") {
            override fun unset() {
                _globalSerializationEnv.set(null)
                inVMExecutors.remove(this)
            }
        }.also {
            _globalSerializationEnv.set(it)
        }
    } else {
        rigorousMock<GlobalSerializationEnvironment>().also {
            doNothing().whenever(it).unset()
        }
    }
}

@DoNotImplement
interface GlobalSerializationEnvironment : SerializationEnvironment {
    /** Unset this environment. */
    fun unset()
}

