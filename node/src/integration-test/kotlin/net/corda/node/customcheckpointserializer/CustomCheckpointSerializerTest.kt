package net.corda.node.customcheckpointserializer

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.generateKeyPair
import net.corda.core.serialization.EncodingWhitelist
import net.corda.core.serialization.internal.CheckpointSerializationContext
import net.corda.core.serialization.internal.checkpointDeserialize
import net.corda.core.serialization.internal.checkpointSerialize
import net.corda.coretesting.internal.rigorousMock
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.CheckpointSerializationContextImpl
import net.corda.serialization.internal.CordaSerializationEncoding
import net.corda.testing.core.internal.CheckpointSerializationEnvironmentRule
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CustomCheckpointSerializerTest(private val compression: CordaSerializationEncoding?) {
    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun compression() = arrayOf<CordaSerializationEncoding?>(null) + CordaSerializationEncoding.values()
    }

    @get:Rule
    val serializationRule = CheckpointSerializationEnvironmentRule(inheritable = true)
    private val context: CheckpointSerializationContext = CheckpointSerializationContextImpl(
            deserializationClassLoader = javaClass.classLoader,
            whitelist = AllWhitelist,
            properties = emptyMap(),
            objectReferencesEnabled = true,
            encoding = compression,
            encodingWhitelist = rigorousMock<EncodingWhitelist>().also {
                if (compression != null) doReturn(true).whenever(it).acceptEncoding(compression)
            },
            checkpointCustomSerializers = listOf(
                    TestCorDapp.TestAbstractClassSerializer(),
                    TestCorDapp.TestClassSerializer(),
                    TestCorDapp.TestInterfaceSerializer(),
                    TestCorDapp.TestFinalClassSerializer(),
                    TestCorDapp.BrokenPublicKeySerializer()
            )
    )

    @Test(timeout=300_000)
    fun `test custom checkpoint serialization`() {
        testBrokenMapSerialization(DifficultToSerialize.BrokenMapClass())
    }

    @Test(timeout=300_000)
    fun `test custom checkpoint serialization using interface`() {
        testBrokenMapSerialization(DifficultToSerialize.BrokenMapInterfaceImpl())
    }

    @Test(timeout=300_000)
    fun `test custom checkpoint serialization using abstract class`() {
        testBrokenMapSerialization(DifficultToSerialize.BrokenMapAbstractImpl())
    }

    @Test(timeout=300_000)
    fun `test custom checkpoint serialization using final class`() {
        testBrokenMapSerialization(DifficultToSerialize.BrokenMapFinal())
    }

    @Test(timeout=300_000)
    fun `test PublicKey serializer has not been overridden`() {

        val publicKey = generateKeyPair().public

        // Serialize/deserialize
        val checkpoint = publicKey.checkpointSerialize(context)
        val deserializedCheckpoint = checkpoint.checkpointDeserialize(context)

        // Check the elements are as expected
        Assert.assertArrayEquals(publicKey.encoded, deserializedCheckpoint.encoded)
    }


    private fun testBrokenMapSerialization(brokenMap : MutableMap<String, String>): MutableMap<String, String> {
        // Add elements to the map
        brokenMap.putAll(mapOf("key" to "value"))

        // Serialize/deserialize
        val checkpoint = brokenMap.checkpointSerialize(context)
        val deserializedCheckpoint = checkpoint.checkpointDeserialize(context)

        // Check the elements are as expected
        Assert.assertEquals(1, deserializedCheckpoint.size)
        Assert.assertEquals("value", deserializedCheckpoint.get("key"))

        // Return map for extra checks
        return deserializedCheckpoint
    }
}

