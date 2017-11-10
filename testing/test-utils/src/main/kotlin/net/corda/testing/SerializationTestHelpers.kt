package net.corda.testing

import com.nhaarman.mockito_kotlin.doNothing
import com.nhaarman.mockito_kotlin.whenever
import net.corda.client.rpc.internal.KryoClientSerializationScheme
import net.corda.core.serialization.internal.*
import net.corda.node.serialization.KryoServerSerializationScheme
import net.corda.nodeapi.internal.serialization.*
import net.corda.nodeapi.internal.serialization.amqp.AMQPClientSerializationScheme
import net.corda.nodeapi.internal.serialization.amqp.AMQPServerSerializationScheme
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** @param inheritable whether new threads inherit the environment, use sparingly. */
class SerializationEnvironmentRule(private val inheritable: Boolean = false) : TestRule {
    val env: SerializationEnvironment = createTestSerializationEnv()
    override fun apply(base: Statement, description: Description?) = object : Statement() {
        override fun evaluate() = env.asContextEnv(inheritable) {
            base.evaluate()
        }
    }
}

interface GlobalSerializationEnvironment : SerializationEnvironment {
    /** Unset this environment. */
    fun unset()
}

/** @param inheritable whether new threads inherit the environment, use sparingly. */
fun <T> withTestSerialization(inheritable: Boolean = false, callable: (SerializationEnvironment) -> T): T {
    return createTestSerializationEnv().asContextEnv(inheritable, callable)
}

private fun <T> SerializationEnvironment.asContextEnv(inheritable: Boolean, callable: (SerializationEnvironment) -> T): T {
    val property = if (inheritable) _inheritableContextSerializationEnv else _contextSerializationEnv
    property.set(this)
    try {
        return callable(this)
    } finally {
        property.set(null)
    }
}

/**
 * For example your test class uses [SerializationEnvironmentRule] but you want to turn it off for one method.
 * Use sparingly, ideally a test class shouldn't mix serialization init mechanisms.
 */
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
fun setGlobalSerialization(armed: Boolean): GlobalSerializationEnvironment {
    return if (armed) {
        object : GlobalSerializationEnvironment, SerializationEnvironment by createTestSerializationEnv() {
            override fun unset() {
                _globalSerializationEnv.set(null)
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
