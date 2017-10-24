package net.corda.testing

import com.nhaarman.mockito_kotlin.doNothing
import com.nhaarman.mockito_kotlin.whenever
import net.corda.client.rpc.internal.KryoClientSerializationScheme
import net.corda.core.serialization.*
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.node.serialization.KryoServerSerializationScheme
import net.corda.nodeapi.internal.serialization.*
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** @param inheritable whether new threads inherit the environment, use sparingly. */
class SerializationEnvironmentRule(private val inheritable: Boolean = false) : TestRule {
    lateinit var env: SerializationEnvironment
    override fun apply(base: Statement, description: Description?) = object : Statement() {
        override fun evaluate() = withTestSerialization(inheritable) {
            env = it
            base.evaluate()
        }
    }
}

interface DisposableSerializationEnvironment : SerializationEnvironment {
    /** Unregister this environment. */
    fun dispose()
}

/** @param inheritable whether new threads inherit the environment, use sparingly. */
fun <T> withTestSerialization(inheritable: Boolean = false, callable: (SerializationEnvironment) -> T): T {
    val property = if (inheritable) _inheritableContextSerializationEnv else _contextSerializationEnv
    val env = createTestSerializationEnv()
    property.set(env)
    try {
        return callable(env)
    } finally {
        property.set(null)
    }
}

/** For example your test class uses [SerializationEnvironmentRule] but you want to turn it off for one method. */
fun <T> withoutTestSerialization(callable: () -> T): T {
    val (property, env) = listOf(_contextSerializationEnv, _inheritableContextSerializationEnv).map { Pair(it, it.get()) }.single { it.second != null }
    property.set(null)
    try {
        return callable()
    } finally {
        property.set(env)
    }
}

/**
 * Should only be used by Driver and MockNode.
 * @param armed true to install, false to do nothing and return a dummy env.
 */
fun installNodeSerialization(armed: Boolean = true): DisposableSerializationEnvironment {
    return if (armed) {
        object : DisposableSerializationEnvironment, SerializationEnvironment by createTestSerializationEnv() {
            override fun dispose() {
                nodeSerializationEnv = null
            }
        }.also {
            nodeSerializationEnv = it
        }
    } else {
        rigorousMock<DisposableSerializationEnvironment>().also {
            doNothing().whenever(it).dispose()
        }
    }
}

private fun createTestSerializationEnv() = SerializationEnvironmentImpl(
        SerializationFactoryImpl().apply {
            registerScheme(KryoClientSerializationScheme())
            registerScheme(KryoServerSerializationScheme())
            registerScheme(AMQPClientSerializationScheme())
            registerScheme(AMQPServerSerializationScheme())
        },
        if (isAmqpEnabled()) AMQP_P2P_CONTEXT else KRYO_P2P_CONTEXT,
        KRYO_RPC_SERVER_CONTEXT,
        KRYO_RPC_CLIENT_CONTEXT,
        if (isAmqpEnabled()) AMQP_STORAGE_CONTEXT else KRYO_STORAGE_CONTEXT,
        KRYO_CHECKPOINT_CONTEXT)

private const val AMQP_ENABLE_PROP_NAME = "net.corda.testing.amqp.enable"

// TODO: Remove usages of this function when we fully switched to AMQP
private fun isAmqpEnabled(): Boolean = java.lang.Boolean.getBoolean(AMQP_ENABLE_PROP_NAME)
