package net.corda.coretests.transactions

import net.corda.core.contracts.Contract
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.declaredField
import net.corda.core.internal.isAttachmentTrusted
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.AttachmentsClassLoaderBuilder
import net.corda.core.serialization.serialize
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.OpaqueBytes
import net.corda.isolated.contracts.DummyContractBackdoor
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.fakeAttachment
import net.corda.testing.services.MockAttachmentStorage
import org.apache.commons.io.IOUtils
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.NotSerializableException
import java.net.URL
import kotlin.test.assertFailsWith

class AttachmentsClassLoaderSerializationTests {

    companion object {
        val ISOLATED_CONTRACTS_JAR_PATH: URL = AttachmentsClassLoaderSerializationTests::class.java.getResource("/isolated.jar")
        private const val ISOLATED_CONTRACT_CLASS_NAME = "net.corda.isolated.contracts.AnotherDummyContract"
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val storage = MockAttachmentStorage()

    @Test
    fun `Can serialize and deserialize with an attachment classloader`() {

        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
        val MEGA_CORP = TestIdentity(CordaX500Name("MegaCorp", "London", "GB")).party

        val isolatedId = storage.importAttachment(ISOLATED_CONTRACTS_JAR_PATH.openStream(), "app", "isolated.jar")
        val att1 = storage.importAttachment(fakeAttachment("file1.txt", "some data").inputStream(), "app", "file1.jar")
        val att2 = storage.importAttachment(fakeAttachment("file2.txt", "some other data").inputStream(), "app", "file2.jar")

        val serialisedState = AttachmentsClassLoaderBuilder.withAttachmentsClassloaderContext(
                arrayOf(isolatedId, att1, att2).map { storage.openAttachment(it)!! },
                testNetworkParameters(),
                SecureHash.zeroHash,
                { isAttachmentTrusted(it, storage) }) { classLoader ->
            val contractClass = Class.forName(ISOLATED_CONTRACT_CLASS_NAME, true, classLoader)
            val contract = contractClass.newInstance() as Contract
            assertEquals("helloworld", contract.declaredField<Any?>("magicString").value)

            val txt = IOUtils.toString(classLoader.getResourceAsStream("file1.txt"), Charsets.UTF_8.name())
            assertEquals("some data", txt)

            val state = (contract as DummyContractBackdoor).generateInitial(MEGA_CORP.ref(1), 1, DUMMY_NOTARY).outputStates().first()
            val serialisedState = state.serialize()

            val state1 = serialisedState.deserialize()
            assertEquals(state, state1)
            serialisedState
        }

        assertFailsWith<NotSerializableException> {
            serialisedState.deserialize()
        }
    }

    // These tests are not Attachment specific. Should they be removed?
    @Test
    fun `test serialization of SecureHash`() {
        val secureHash = SecureHash.randomSHA256()
        val bytes = secureHash.serialize()
        val copiedSecuredHash = bytes.deserialize()

        assertEquals(secureHash, copiedSecuredHash)
    }

    @Test
    fun `test serialization of OpaqueBytes`() {
        val opaqueBytes = OpaqueBytes("0123456789".toByteArray())
        val bytes = opaqueBytes.serialize()
        val copiedOpaqueBytes = bytes.deserialize()

        assertEquals(opaqueBytes, copiedOpaqueBytes)
    }

    @Test
    fun `test serialization of sub-sequence OpaqueBytes`() {
        val bytesSequence = ByteSequence.of("0123456789".toByteArray(), 3, 2)
        val bytes = bytesSequence.serialize()
        val copiedBytesSequence = bytes.deserialize()

        assertEquals(bytesSequence, copiedBytesSequence)
    }
}

