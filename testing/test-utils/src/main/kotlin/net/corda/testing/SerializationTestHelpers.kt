package net.corda.testing

import net.corda.client.rpc.internal.KryoClientSerializationScheme
import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.*
import net.corda.core.utilities.ByteSequence
import net.corda.node.serialization.KryoServerSerializationScheme
import net.corda.nodeapi.internal.serialization.*

inline fun <T> withTestSerialization(block: () -> T): T {
    initialiseTestSerialization()
    try {
        return block()
    } finally {
        resetTestSerialization()
    }
}

fun initialiseTestSerialization() {
    // Stop the CordaRPCClient from trying to setup the defaults as we're about to do it now
    KryoClientSerializationScheme.isInitialised.set(true)
    // Check that everything is configured for testing with mutable delegating instances.
    try {
        check(SerializationDefaults.SERIALIZATION_FACTORY is TestSerializationFactory)
    } catch (e: IllegalStateException) {
        SerializationDefaults.SERIALIZATION_FACTORY = TestSerializationFactory()
    }
    try {
        check(SerializationDefaults.P2P_CONTEXT is TestSerializationContext)
    } catch (e: IllegalStateException) {
        SerializationDefaults.P2P_CONTEXT = TestSerializationContext()
    }
    try {
        check(SerializationDefaults.RPC_SERVER_CONTEXT is TestSerializationContext)
    } catch (e: IllegalStateException) {
        SerializationDefaults.RPC_SERVER_CONTEXT = TestSerializationContext()
    }
    try {
        check(SerializationDefaults.RPC_CLIENT_CONTEXT is TestSerializationContext)
    } catch (e: IllegalStateException) {
        SerializationDefaults.RPC_CLIENT_CONTEXT = TestSerializationContext()
    }
    try {
        check(SerializationDefaults.STORAGE_CONTEXT is TestSerializationContext)
    } catch (e: IllegalStateException) {
        SerializationDefaults.STORAGE_CONTEXT = TestSerializationContext()
    }
    try {
        check(SerializationDefaults.CHECKPOINT_CONTEXT is TestSerializationContext)
    } catch (e: IllegalStateException) {
        SerializationDefaults.CHECKPOINT_CONTEXT = TestSerializationContext()
    }

    // Check that the previous test, if there was one, cleaned up after itself.
    // IF YOU SEE THESE MESSAGES, THEN IT MEANS A TEST HAS NOT CALLED resetTestSerialization()
    check((SerializationDefaults.SERIALIZATION_FACTORY as TestSerializationFactory).delegate == null, { "Expected uninitialised serialization framework but found it set from: ${SerializationDefaults.SERIALIZATION_FACTORY}" })
    check((SerializationDefaults.P2P_CONTEXT as TestSerializationContext).delegate == null, { "Expected uninitialised serialization framework but found it set from: ${SerializationDefaults.P2P_CONTEXT}" })
    check((SerializationDefaults.RPC_SERVER_CONTEXT as TestSerializationContext).delegate == null, { "Expected uninitialised serialization framework but found it set from: ${SerializationDefaults.RPC_SERVER_CONTEXT}" })
    check((SerializationDefaults.RPC_CLIENT_CONTEXT as TestSerializationContext).delegate == null, { "Expected uninitialised serialization framework but found it set from: ${SerializationDefaults.RPC_CLIENT_CONTEXT}" })
    check((SerializationDefaults.STORAGE_CONTEXT as TestSerializationContext).delegate == null, { "Expected uninitialised serialization framework but found it set from: ${SerializationDefaults.STORAGE_CONTEXT}" })
    check((SerializationDefaults.CHECKPOINT_CONTEXT as TestSerializationContext).delegate == null, { "Expected uninitialised serialization framework but found it set from: ${SerializationDefaults.CHECKPOINT_CONTEXT}" })

    // Now configure all the testing related delegates.
    (SerializationDefaults.SERIALIZATION_FACTORY as TestSerializationFactory).delegate = SerializationFactoryImpl().apply {
        registerScheme(KryoClientSerializationScheme())
        registerScheme(KryoServerSerializationScheme())
        registerScheme(AMQPClientSerializationScheme())
        registerScheme(AMQPServerSerializationScheme())
    }

    (SerializationDefaults.P2P_CONTEXT as TestSerializationContext).delegate = if (isAmqpEnabled()) AMQP_P2P_CONTEXT else KRYO_P2P_CONTEXT
    (SerializationDefaults.RPC_SERVER_CONTEXT as TestSerializationContext).delegate = KRYO_RPC_SERVER_CONTEXT
    (SerializationDefaults.RPC_CLIENT_CONTEXT as TestSerializationContext).delegate = KRYO_RPC_CLIENT_CONTEXT
    (SerializationDefaults.STORAGE_CONTEXT as TestSerializationContext).delegate = if (isAmqpEnabled()) AMQP_STORAGE_CONTEXT else KRYO_STORAGE_CONTEXT
    (SerializationDefaults.CHECKPOINT_CONTEXT as TestSerializationContext).delegate = KRYO_CHECKPOINT_CONTEXT
}

private const val AMQP_ENABLE_PROP_NAME = "net.corda.testing.amqp.enable"

// TODO: Remove usages of this function when we fully switched to AMQP
private fun isAmqpEnabled(): Boolean = java.lang.Boolean.getBoolean(AMQP_ENABLE_PROP_NAME)

fun resetTestSerialization() {
    (SerializationDefaults.SERIALIZATION_FACTORY as TestSerializationFactory).delegate = null
    (SerializationDefaults.P2P_CONTEXT as TestSerializationContext).delegate = null
    (SerializationDefaults.RPC_SERVER_CONTEXT as TestSerializationContext).delegate = null
    (SerializationDefaults.RPC_CLIENT_CONTEXT as TestSerializationContext).delegate = null
    (SerializationDefaults.STORAGE_CONTEXT as TestSerializationContext).delegate = null
    (SerializationDefaults.CHECKPOINT_CONTEXT as TestSerializationContext).delegate = null
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

    override fun withPreferredSerializationVersion(versionHeader: ByteSequence): SerializationContext {
        return TestSerializationContext().apply { delegate = this@TestSerializationContext.delegate!!.withPreferredSerializationVersion(versionHeader) }
    }

    override fun withAttachmentsClassLoader(attachmentHashes: List<SecureHash>): SerializationContext {
        return TestSerializationContext().apply { delegate = this@TestSerializationContext.delegate!!.withAttachmentsClassLoader(attachmentHashes) }
    }
}
