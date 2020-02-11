package net.corda.deterministic.verifier

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationContext.UseCase.P2P
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal._driverSerializationEnv
import net.corda.serialization.internal.*
import net.corda.serialization.internal.amqp.*
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
        _driverSerializationEnv.set(createTestSerializationEnv())
    }

    private fun clear() {
        _driverSerializationEnv.set(null)
    }

    private fun createTestSerializationEnv(): SerializationEnvironment {
        val factory = SerializationFactoryImpl(mutableMapOf()).apply {
            registerScheme(AMQPSerializationScheme(emptySet(), emptySet(), AccessOrderLinkedHashMap(128)))
        }
        return SerializationEnvironment.with(factory, AMQP_P2P_CONTEXT)
    }

    private class AMQPSerializationScheme(
            cordappCustomSerializers: Set<SerializationCustomSerializer<*, *>>,
            cordappSerializationWhitelists: Set<SerializationWhitelist>,
            serializerFactoriesForContexts: AccessOrderLinkedHashMap<SerializationFactoryCacheKey, SerializerFactory>
    ) : AbstractAMQPSerializationScheme(cordappCustomSerializers, cordappSerializationWhitelists, serializerFactoriesForContexts) {
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
