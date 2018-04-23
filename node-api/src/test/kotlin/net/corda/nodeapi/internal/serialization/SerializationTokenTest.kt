/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.io.Output
import net.corda.core.serialization.*
import net.corda.core.utilities.OpaqueBytes
import net.corda.nodeapi.internal.serialization.kryo.CordaKryo
import net.corda.nodeapi.internal.serialization.kryo.DefaultKryoCustomizer
import net.corda.nodeapi.internal.serialization.kryo.kryoMagic
import net.corda.testing.internal.rigorousMock
import net.corda.testing.core.SerializationEnvironmentRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream

class SerializationTokenTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    private lateinit var factory: SerializationFactory
    private lateinit var context: SerializationContext

    @Before
    fun setup() {
        factory = testSerialization.serializationFactory
        context = testSerialization.checkpointContext.withWhitelisted(SingletonSerializationToken::class.java)
    }

    // Large tokenizable object so we can tell from the smaller number of serialized bytes it was actually tokenized
    private class LargeTokenizable : SingletonSerializeAsToken() {
        val bytes = OpaqueBytes(ByteArray(1024))

        val numBytes: Int
            get() = bytes.size

        override fun hashCode() = bytes.size

        override fun equals(other: Any?) = other is LargeTokenizable && other.bytes.size == this.bytes.size
    }

    private fun serializeAsTokenContext(toBeTokenized: Any) = SerializeAsTokenContextImpl(toBeTokenized, factory, context, rigorousMock())
    @Test
    fun `write token and read tokenizable`() {
        val tokenizableBefore = LargeTokenizable()
        val context = serializeAsTokenContext(tokenizableBefore)
        val testContext = this.context.withTokenContext(context)

        val serializedBytes = tokenizableBefore.serialize(factory, testContext)
        assertThat(serializedBytes.size).isLessThan(tokenizableBefore.numBytes)
        val tokenizableAfter = serializedBytes.deserialize(factory, testContext)
        assertThat(tokenizableAfter).isSameAs(tokenizableBefore)
    }

    private class UnitSerializeAsToken : SingletonSerializeAsToken()

    @Test
    fun `write and read singleton`() {
        val tokenizableBefore = UnitSerializeAsToken()
        val context = serializeAsTokenContext(tokenizableBefore)
        val testContext = this.context.withTokenContext(context)
        val serializedBytes = tokenizableBefore.serialize(factory, testContext)
        val tokenizableAfter = serializedBytes.deserialize(factory, testContext)
        assertThat(tokenizableAfter).isSameAs(tokenizableBefore)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun `new token encountered after context init`() {
        val tokenizableBefore = UnitSerializeAsToken()
        val context = serializeAsTokenContext(emptyList<Any>())
        val testContext = this.context.withTokenContext(context)
        tokenizableBefore.serialize(factory, testContext)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun `deserialize unregistered token`() {
        val tokenizableBefore = UnitSerializeAsToken()
        val context = serializeAsTokenContext(emptyList<Any>())
        val testContext = this.context.withTokenContext(context)
        val serializedBytes = tokenizableBefore.toToken(serializeAsTokenContext(emptyList<Any>())).serialize(factory, testContext)
        serializedBytes.deserialize(factory, testContext)
    }

    @Test(expected = KryoException::class)
    fun `no context set`() {
        val tokenizableBefore = UnitSerializeAsToken()
        tokenizableBefore.serialize(factory, context)
    }

    @Test(expected = KryoException::class)
    fun `deserialize non-token`() {
        val tokenizableBefore = UnitSerializeAsToken()
        val context = serializeAsTokenContext(tokenizableBefore)
        val testContext = this.context.withTokenContext(context)

        val kryo: Kryo = DefaultKryoCustomizer.customize(CordaKryo(CordaClassResolver(this.context)))
        val stream = ByteArrayOutputStream()
        Output(stream).use {
            kryoMagic.writeTo(it)
            SectionId.ALT_DATA_AND_STOP.writeTo(it)
            kryo.writeClass(it, SingletonSerializeAsToken::class.java)
            kryo.writeObject(it, emptyList<Any>())
        }
        val serializedBytes = SerializedBytes<Any>(stream.toByteArray())
        serializedBytes.deserialize(factory, testContext)
    }

    private class WrongTypeSerializeAsToken : SerializeAsToken {
        object UnitSerializationToken : SerializationToken {
            override fun fromToken(context: SerializeAsTokenContext): Any = UnitSerializeAsToken()
        }

        override fun toToken(context: SerializeAsTokenContext): SerializationToken = UnitSerializationToken
    }

    @Test(expected = KryoException::class)
    fun `token returns unexpected type`() {
        val tokenizableBefore = WrongTypeSerializeAsToken()
        val context = serializeAsTokenContext(tokenizableBefore)
        val testContext = this.context.withTokenContext(context)
        val serializedBytes = tokenizableBefore.serialize(factory, testContext)
        serializedBytes.deserialize(factory, testContext)
    }
}
