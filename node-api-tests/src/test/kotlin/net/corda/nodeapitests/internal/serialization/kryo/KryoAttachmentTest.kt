package net.corda.nodeapitests.internal.serialization.kryo

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.EncodingWhitelist
import net.corda.core.serialization.internal.CheckpointSerializationContext
import net.corda.core.serialization.internal.checkpointDeserialize
import net.corda.core.serialization.internal.checkpointSerialize
import net.corda.coretesting.internal.rigorousMock
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.CheckpointSerializationContextImpl
import net.corda.serialization.internal.CordaSerializationEncoding
import net.corda.testing.core.internal.CheckpointSerializationEnvironmentRule
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.InputStream

@RunWith(Parameterized::class)
class KryoAttachmentTest(private val compression: CordaSerializationEncoding?) {
    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun compression() = arrayOf<CordaSerializationEncoding?>(null) + CordaSerializationEncoding.values()
    }


    @get:Rule
    val serializationRule = CheckpointSerializationEnvironmentRule()
    private lateinit var context: CheckpointSerializationContext

    @Before
    fun setup() {
        context = CheckpointSerializationContextImpl(
                javaClass.classLoader,
                AllWhitelist,
                emptyMap(),
                true,
                compression,
                rigorousMock<EncodingWhitelist>().also {
                    if (compression != null) doReturn(true).whenever(it).acceptEncoding(compression)
                })
    }

    @Test(timeout=300_000)
    fun `HashCheckingStream (de)serialize`() {
        val rubbish = ByteArray(12345) { (it * it * 0.12345).toByte() }
        val readRubbishStream: InputStream = NodeAttachmentService.HashCheckingStream(
                SecureHash.sha256(rubbish),
                rubbish.size,
                rubbish.inputStream()
        ).checkpointSerialize(context).checkpointDeserialize(context)
        for (i in 0..12344) {
            Assert.assertEquals(rubbish[i], readRubbishStream.read().toByte())
        }
        Assert.assertEquals(-1, readRubbishStream.read())
    }
}