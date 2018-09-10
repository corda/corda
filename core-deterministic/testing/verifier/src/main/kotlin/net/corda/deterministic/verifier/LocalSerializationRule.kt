package net.corda.deterministic.verifier

import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationContext.UseCase.P2P
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.serialization.internal.*
import net.corda.serialization.internal.amqp.AbstractAMQPSerializationScheme
import net.corda.serialization.internal.amqp.AccessOrderLinkedHashMap
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.amqpMagic
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

class LocalSerializationRule(private val label: String) : TestRule {
    constructor(klass: KClass<*>) : this(klass.jvmName)

    private companion object {
        private val AMQP_P2P_CONTEXT = SerializationContextImpl(
                amqpMagic,
                LocalSerializationRule::class.java.classLoader,
                GlobalTransientClassWhiteList(BuiltInExceptionsWhitelist()),
                emptyMap(),
                true,
                P2P,
                null
        )
    }

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                init()
                try {
                    base.evaluate()
                } finally {
                    clear()
                }
            }
        }
    }

    fun reset() {
        clear()
        init()
    }

    private fun init() {
        _contextSerializationEnv.set(createTestSerializationEnv())
    }

    private fun clear() {
        _contextSerializationEnv.set(null)
    }

    private fun createTestSerializationEnv(): SerializationEnvironmentImpl {
        val factory = SerializationFactoryImpl(mutableMapOf()).apply {
            registerScheme(AMQPSerializationScheme(emptySet(), AccessOrderLinkedHashMap(128)))
        }
        return object : SerializationEnvironmentImpl(factory, AMQP_P2P_CONTEXT) {
            override fun toString() = "testSerializationEnv($label)"
        }
    }

    private class AMQPSerializationScheme(
            cordappCustomSerializers: Set<SerializationCustomSerializer<*, *>>,
            serializerFactoriesForContexts: AccessOrderLinkedHashMap<Pair<ClassWhitelist, ClassLoader>, SerializerFactory>
    ) : AbstractAMQPSerializationScheme(cordappCustomSerializers, serializerFactoriesForContexts) {
        override fun rpcServerSerializerFactory(context: SerializationContext): SerializerFactory {
            throw UnsupportedOperationException()
        }

        override fun rpcClientSerializerFactory(context: SerializationContext): SerializerFactory {
            throw UnsupportedOperationException()
        }

        override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean {
            return canDeserializeVersion(magic) && target == P2P
        }
    }
}
