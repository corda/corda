package net.corda.testing

import com.nhaarman.mockito_kotlin.doNothing
import com.nhaarman.mockito_kotlin.whenever
import net.corda.client.rpc.internal.KryoClientSerializationScheme
import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.*
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.utilities.ByteSequence
import net.corda.node.serialization.KryoServerSerializationScheme
import net.corda.nodeapi.internal.serialization.*
import net.corda.nodeapi.internal.serialization.amqp.AMQPClientSerializationScheme
import net.corda.nodeapi.internal.serialization.amqp.AMQPServerSerializationScheme
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class SerializationEnvironmentRule : TestRule {
    lateinit var env: SerializationEnvironment
    override fun apply(base: Statement, description: Description?) = object : Statement() {
        override fun evaluate() = withTestSerialization {
            env = it
            base.evaluate()
        }
    }
}

interface TestSerializationEnvironment : SerializationEnvironment {
    fun resetTestSerialization()
}

fun <T> withTestSerialization(block: (SerializationEnvironment) -> T): T {
    val env = initialiseTestSerializationImpl()
    try {
        return block(env)
    } finally {
        env.resetTestSerialization()
    }
}

/** @param armed true to init, false to do nothing and return a dummy env. */
fun initialiseTestSerialization(armed: Boolean): TestSerializationEnvironment {
    return if (armed) {
        val env = initialiseTestSerializationImpl()
        object : TestSerializationEnvironment, SerializationEnvironment by env {
            override fun resetTestSerialization() = env.resetTestSerialization()
        }
    } else {
        rigorousMock<TestSerializationEnvironment>().also {
            doNothing().whenever(it).resetTestSerialization()
        }
    }
}

private fun initialiseTestSerializationImpl() = SerializationDefaults.apply {
    // Stop the CordaRPCClient from trying to setup the defaults as we're about to do it now
    KryoClientSerializationScheme.isInitialised.set(true)
    // Check that everything is configured for testing with mutable delegating instances.
    try {
        check(SERIALIZATION_FACTORY is TestSerializationFactory)
    } catch (e: IllegalStateException) {
        SERIALIZATION_FACTORY = TestSerializationFactory()
    }
    try {
        check(P2P_CONTEXT is TestSerializationContext)
    } catch (e: IllegalStateException) {
        P2P_CONTEXT = TestSerializationContext()
    }
    try {
        check(RPC_SERVER_CONTEXT is TestSerializationContext)
    } catch (e: IllegalStateException) {
        RPC_SERVER_CONTEXT = TestSerializationContext()
    }
    try {
        check(RPC_CLIENT_CONTEXT is TestSerializationContext)
    } catch (e: IllegalStateException) {
        RPC_CLIENT_CONTEXT = TestSerializationContext()
    }
    try {
        check(STORAGE_CONTEXT is TestSerializationContext)
    } catch (e: IllegalStateException) {
        STORAGE_CONTEXT = TestSerializationContext()
    }
    try {
        check(CHECKPOINT_CONTEXT is TestSerializationContext)
    } catch (e: IllegalStateException) {
        CHECKPOINT_CONTEXT = TestSerializationContext()
    }

    // Check that the previous test, if there was one, cleaned up after itself.
    // IF YOU SEE THESE MESSAGES, THEN IT MEANS A TEST HAS NOT CALLED resetTestSerialization()
    check((SERIALIZATION_FACTORY as TestSerializationFactory).delegate == null, { "Expected uninitialised serialization framework but found it set from: $SERIALIZATION_FACTORY" })
    check((P2P_CONTEXT as TestSerializationContext).delegate == null, { "Expected uninitialised serialization framework but found it set from: $P2P_CONTEXT" })
    check((RPC_SERVER_CONTEXT as TestSerializationContext).delegate == null, { "Expected uninitialised serialization framework but found it set from: $RPC_SERVER_CONTEXT" })
    check((RPC_CLIENT_CONTEXT as TestSerializationContext).delegate == null, { "Expected uninitialised serialization framework but found it set from: $RPC_CLIENT_CONTEXT" })
    check((STORAGE_CONTEXT as TestSerializationContext).delegate == null, { "Expected uninitialised serialization framework but found it set from: $STORAGE_CONTEXT" })
    check((CHECKPOINT_CONTEXT as TestSerializationContext).delegate == null, { "Expected uninitialised serialization framework but found it set from: $CHECKPOINT_CONTEXT" })

    // Now configure all the testing related delegates.
    (SERIALIZATION_FACTORY as TestSerializationFactory).delegate = SerializationFactoryImpl().apply {
        registerScheme(KryoClientSerializationScheme())
        registerScheme(KryoServerSerializationScheme())
        registerScheme(AMQPClientSerializationScheme())
        registerScheme(AMQPServerSerializationScheme())
    }

    (P2P_CONTEXT as TestSerializationContext).delegate = if (isAmqpEnabled()) AMQP_P2P_CONTEXT else KRYO_P2P_CONTEXT
    (RPC_SERVER_CONTEXT as TestSerializationContext).delegate = KRYO_RPC_SERVER_CONTEXT
    (RPC_CLIENT_CONTEXT as TestSerializationContext).delegate = KRYO_RPC_CLIENT_CONTEXT
    (STORAGE_CONTEXT as TestSerializationContext).delegate = if (isAmqpEnabled()) AMQP_STORAGE_CONTEXT else KRYO_STORAGE_CONTEXT
    (CHECKPOINT_CONTEXT as TestSerializationContext).delegate = KRYO_CHECKPOINT_CONTEXT
}

private const val AMQP_ENABLE_PROP_NAME = "net.corda.testing.amqp.enable"

// TODO: Remove usages of this function when we fully switched to AMQP
private fun isAmqpEnabled(): Boolean = java.lang.Boolean.getBoolean(AMQP_ENABLE_PROP_NAME)

private fun SerializationDefaults.resetTestSerialization() {
    (SERIALIZATION_FACTORY as TestSerializationFactory).delegate = null
    (P2P_CONTEXT as TestSerializationContext).delegate = null
    (RPC_SERVER_CONTEXT as TestSerializationContext).delegate = null
    (RPC_CLIENT_CONTEXT as TestSerializationContext).delegate = null
    (STORAGE_CONTEXT as TestSerializationContext).delegate = null
    (CHECKPOINT_CONTEXT as TestSerializationContext).delegate = null
}

class TestSerializationFactory : SerializationFactory() {
    var delegate: SerializationFactory? = null
        set(value) {
            field = value
            stackTrace = Exception().stackTrace.asList()
        }
    private var stackTrace: List<StackTraceElement>? = null

    override fun toString(): String = stackTrace?.joinToString("\n") ?: "null"

    override fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T {
        return delegate!!.deserialize(byteSequence, clazz, context)
    }

    override fun <T : Any> deserializeWithCompatibleContext(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): ObjectWithCompatibleContext<T> {
        return delegate!!.deserializeWithCompatibleContext(byteSequence, clazz, context)
    }

    override fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T> {
        return delegate!!.serialize(obj, context)
    }
}

class TestSerializationContext : SerializationContext {
    var delegate: SerializationContext? = null
        set(value) {
            field = value
            stackTrace = Exception().stackTrace.asList()
        }
    private var stackTrace: List<StackTraceElement>? = null

    override fun toString(): String = stackTrace?.joinToString("\n") ?: "null"

    override val preferredSerializationVersion: ByteSequence
        get() = delegate!!.preferredSerializationVersion
    override val deserializationClassLoader: ClassLoader
        get() = delegate!!.deserializationClassLoader
    override val whitelist: ClassWhitelist
        get() = delegate!!.whitelist
    override val properties: Map<Any, Any>
        get() = delegate!!.properties
    override val objectReferencesEnabled: Boolean
        get() = delegate!!.objectReferencesEnabled
    override val useCase: SerializationContext.UseCase
        get() = delegate!!.useCase

    override fun withProperty(property: Any, value: Any): SerializationContext {
        return TestSerializationContext().apply { delegate = this@TestSerializationContext.delegate!!.withProperty(property, value) }
    }

    override fun withoutReferences(): SerializationContext {
        return TestSerializationContext().apply { delegate = this@TestSerializationContext.delegate!!.withoutReferences() }
    }

    override fun withClassLoader(classLoader: ClassLoader): SerializationContext {
        return TestSerializationContext().apply { delegate = this@TestSerializationContext.delegate!!.withClassLoader(classLoader) }
    }

    override fun withWhitelisted(clazz: Class<*>): SerializationContext {
        return TestSerializationContext().apply { delegate = this@TestSerializationContext.delegate!!.withWhitelisted(clazz) }
    }

    override fun withPreferredSerializationVersion(versionHeader: VersionHeader): SerializationContext {
        return TestSerializationContext().apply { delegate = this@TestSerializationContext.delegate!!.withPreferredSerializationVersion(versionHeader) }
    }

    override fun withAttachmentsClassLoader(attachmentHashes: List<SecureHash>): SerializationContext {
        return TestSerializationContext().apply { delegate = this@TestSerializationContext.delegate!!.withAttachmentsClassLoader(attachmentHashes) }
    }
}
