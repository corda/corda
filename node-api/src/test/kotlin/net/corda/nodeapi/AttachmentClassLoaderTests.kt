package net.corda.nodeapi

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.internal.declaredField
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.serialization.*
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.OpaqueBytes
import net.corda.nodeapi.internal.serialization.SerializeAsTokenContextImpl
import net.corda.nodeapi.internal.serialization.attachmentsClassLoaderEnabledPropertyName
import net.corda.nodeapi.internal.serialization.withTokenContext
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.MEGA_CORP
import net.corda.testing.TestDependencyInjectionBase
import net.corda.testing.kryoSpecific
import net.corda.testing.node.MockAttachmentStorage
import org.apache.commons.io.IOUtils
import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.net.URLClassLoader
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

interface DummyContractBackdoor {
    fun generateInitial(owner: PartyAndReference, magicNumber: Int, notary: Party): TransactionBuilder
    fun inspectState(state: ContractState): Int
}

class AttachmentClassLoaderTests : TestDependencyInjectionBase() {
    companion object {
        val ISOLATED_CONTRACTS_JAR_PATH: URL = AttachmentClassLoaderTests::class.java.getResource("isolated.jar")
        private val ISOLATED_CONTRACT_CLASS_NAME = "net.corda.finance.contracts.isolated.AnotherDummyContract"
        private val ATTACHMENT_PROGRAM_ID = "net.corda.nodeapi.AttachmentClassLoaderTests.AttachmentDummyContract"

        private fun SerializationContext.withAttachmentStorage(attachmentStorage: AttachmentStorage): SerializationContext {
            val serviceHub = mock<ServiceHub>()
            whenever(serviceHub.attachments).thenReturn(attachmentStorage)
            return this.withTokenContext(SerializeAsTokenContextImpl(serviceHub) {}).withProperty(attachmentsClassLoaderEnabledPropertyName, true)
        }
    }

    class AttachmentDummyContract : Contract {
        data class State(val magicNumber: Int = 0) : ContractState {
            override val participants: List<AbstractParty>
                get() = listOf()
        }

        interface Commands : CommandData {
            class Create : TypeOnlyCommandData(), Commands
        }

        override fun verify(tx: LedgerTransaction) {
            // Always accepts.
        }

        fun generateInitial(owner: PartyAndReference, magicNumber: Int, notary: Party): TransactionBuilder {
            val state = State(magicNumber)
            return TransactionBuilder(notary).withItems(StateAndContract(state, ATTACHMENT_PROGRAM_ID), Command(Commands.Create(), owner.party.owningKey))
        }
    }

    fun importJar(storage: AttachmentStorage) = ISOLATED_CONTRACTS_JAR_PATH.openStream().use { storage.importAttachment(it) }

    // These ClassLoaders work together to load 'AnotherDummyContract' in a disposable way, such that even though
    // the class may be on the unit test class path (due to default IDE settings, etc), it won't be loaded into the
    // regular app classloader but rather than ClassLoaderForTests. This helps keep our environment clean and
    // ensures we have precise control over where it's loaded.
    object FilteringClassLoader : ClassLoader() {
        override fun loadClass(name: String, resolve: Boolean): Class<*>? {
            if ("AnotherDummyContract" in name) {
                return null
            } else
                return super.loadClass(name, resolve)
        }
    }

    class ClassLoaderForTests : URLClassLoader(arrayOf(ISOLATED_CONTRACTS_JAR_PATH), FilteringClassLoader)

    @Test
    fun `dynamically load AnotherDummyContract from isolated contracts jar`() {
        val child = ClassLoaderForTests()

        val contractClass = Class.forName(ISOLATED_CONTRACT_CLASS_NAME, true, child)
        val contract = contractClass.newInstance() as Contract

        assertEquals(SecureHash.sha256("helloworld"), contract.declaredField<Any?>("magicString").value)
    }

    fun fakeAttachment(filepath: String, content: String): ByteArray {
        val bs = ByteArrayOutputStream()
        val js = JarOutputStream(bs)
        js.putNextEntry(ZipEntry(filepath))
        js.writer().apply { append(content); flush() }
        js.closeEntry()
        js.close()
        return bs.toByteArray()
    }

    fun readAttachment(attachment: Attachment, filepath: String): ByteArray {
        ByteArrayOutputStream().use {
            attachment.extractFile(filepath, it)
            return it.toByteArray()
        }

    }

    @Test
    fun `test MockAttachmentStorage open as jar`() {
        val storage = MockAttachmentStorage()
        val key = importJar(storage)
        val attachment = storage.openAttachment(key)!!

        val jar = attachment.openAsJAR()

        assertNotNull(jar.nextEntry)
    }

    @Test
    fun `test overlapping file exception`() {
        val storage = MockAttachmentStorage()

        val att0 = importJar(storage)
        val att1 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("file.txt", "some data")))
        val att2 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("file.txt", "some other data")))

        assertFailsWith(AttachmentsClassLoader.OverlappingAttachments::class) {
            AttachmentsClassLoader(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! })
        }
    }

    @Test
    fun `basic`() {
        val storage = MockAttachmentStorage()

        val att0 = importJar(storage)
        val att1 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("file1.txt", "some data")))
        val att2 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("file2.txt", "some other data")))

        val cl = AttachmentsClassLoader(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! })
        val txt = IOUtils.toString(cl.getResourceAsStream("file1.txt"), Charsets.UTF_8.name())
        assertEquals("some data", txt)
    }

    @Test
    fun `Check platform independent path handling in attachment jars`() {
        val storage = MockAttachmentStorage()

        val att1 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("/folder1/foldera/file1.txt", "some data")))
        val att2 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("\\folder1\\folderb\\file2.txt", "some other data")))

        val data1a = readAttachment(storage.openAttachment(att1)!!, "/folder1/foldera/file1.txt")
        Assert.assertArrayEquals("some data".toByteArray(), data1a)

        val data1b = readAttachment(storage.openAttachment(att1)!!, "\\folder1\\foldera\\file1.txt")
        Assert.assertArrayEquals("some data".toByteArray(), data1b)

        val data2a = readAttachment(storage.openAttachment(att2)!!, "\\folder1\\folderb\\file2.txt")
        Assert.assertArrayEquals("some other data".toByteArray(), data2a)

        val data2b = readAttachment(storage.openAttachment(att2)!!, "/folder1/folderb/file2.txt")
        Assert.assertArrayEquals("some other data".toByteArray(), data2b)

    }

    @Test
    fun `loading class AnotherDummyContract`() {
        val storage = MockAttachmentStorage()

        val att0 = importJar(storage)
        val att1 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("file1.txt", "some data")))
        val att2 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("file2.txt", "some other data")))

        val cl = AttachmentsClassLoader(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! }, FilteringClassLoader)
        val contractClass = Class.forName(ISOLATED_CONTRACT_CLASS_NAME, true, cl)
        val contract = contractClass.newInstance() as Contract
        assertEquals(cl, contract.javaClass.classLoader)
        assertEquals(SecureHash.sha256("helloworld"), contract.declaredField<Any?>("magicString").value)
    }


    @Test
    fun `verify that contract DummyContract is in classPath`() {
        val contractClass = Class.forName("net.corda.nodeapi.AttachmentClassLoaderTests\$AttachmentDummyContract")
        val contract = contractClass.newInstance() as Contract

        assertNotNull(contract)
    }

    fun createContract2Cash(): Contract {
        val cl = ClassLoaderForTests()
        val contractClass = Class.forName(ISOLATED_CONTRACT_CLASS_NAME, true, cl)
        return contractClass.newInstance() as Contract
    }

    @Test
    fun `testing Kryo with ClassLoader (with top level class name)`() {
        val contract = createContract2Cash()

        val bytes = contract.serialize()

        val storage = MockAttachmentStorage()

        val att0 = importJar(storage)
        val att1 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("file1.txt", "some data")))
        val att2 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("file2.txt", "some other data")))

        val cl = AttachmentsClassLoader(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! }, FilteringClassLoader)

        val context = SerializationFactory.defaultFactory.defaultContext.withClassLoader(cl).withWhitelisted(contract.javaClass)
        val state2 = bytes.deserialize(context = context)
        assertTrue(state2.javaClass.classLoader is AttachmentsClassLoader)
        assertNotNull(state2)
    }

    // top level wrapper
    @CordaSerializable
    class Data(val contract: Contract)

    @Test
    fun `testing Kryo with ClassLoader (without top level class name)`() {
        val data = Data(createContract2Cash())

        assertNotNull(data.contract)

        val context2 = SerializationFactory.defaultFactory.defaultContext.withWhitelisted(data.contract.javaClass)

        val bytes = data.serialize(context = context2)

        val storage = MockAttachmentStorage()

        val att0 = importJar(storage)
        val att1 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("file1.txt", "some data")))
        val att2 = storage.importAttachment(ByteArrayInputStream(fakeAttachment("file2.txt", "some other data")))

        val cl = AttachmentsClassLoader(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! }, FilteringClassLoader)

        val context = SerializationFactory.defaultFactory.defaultContext.withClassLoader(cl).withWhitelisted(Class.forName(ISOLATED_CONTRACT_CLASS_NAME, true, cl))

        val state2 = bytes.deserialize(context = context)
        assertEquals(cl, state2.contract.javaClass.classLoader)
        assertNotNull(state2)

        // We should be able to load same class from a different class loader and have them be distinct.
        val cl2 = AttachmentsClassLoader(arrayOf(att0, att1, att2).map { storage.openAttachment(it)!! }, FilteringClassLoader)

        val context3 = SerializationFactory.defaultFactory.defaultContext.withClassLoader(cl2).withWhitelisted(Class.forName(ISOLATED_CONTRACT_CLASS_NAME, true, cl2))

        val state3 = bytes.deserialize(context = context3)
        assertEquals(cl2, state3.contract.javaClass.classLoader)
        assertNotNull(state3)
    }

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
        val bytesSequence = ByteSequence.of("0123456789".toByteArray(), 3 ,2)
        val bytes = bytesSequence.serialize()
        val copiedBytesSequence = bytes.deserialize()

        assertEquals(bytesSequence, copiedBytesSequence)
    }

    @Test
    fun `test serialization of WireTransaction with statically loaded contract`() {
        val tx = AttachmentDummyContract().generateInitial(MEGA_CORP.ref(0), 42, DUMMY_NOTARY)
        val wireTransaction = tx.toWireTransaction()
        val bytes = wireTransaction.serialize()
        val copiedWireTransaction = bytes.deserialize()

        assertEquals(1, copiedWireTransaction.outputs.size)
        assertEquals(42, (copiedWireTransaction.outputs[0].data as AttachmentDummyContract.State).magicNumber)
    }

    @Test
    fun `test serialization of WireTransaction with dynamically loaded contract`() {
        val child = ClassLoaderForTests()
        val contractClass = Class.forName(ISOLATED_CONTRACT_CLASS_NAME, true, child)
        val contract = contractClass.newInstance() as DummyContractBackdoor
        val tx = contract.generateInitial(MEGA_CORP.ref(0), 42, DUMMY_NOTARY)
        val storage = MockAttachmentStorage()
        val context = SerializationFactory.defaultFactory.defaultContext.withWhitelisted(contract.javaClass)
                .withWhitelisted(Class.forName("$ISOLATED_CONTRACT_CLASS_NAME\$State", true, child))
                .withWhitelisted(Class.forName("$ISOLATED_CONTRACT_CLASS_NAME\$Commands\$Create", true, child))
                .withAttachmentStorage(storage)

        // todo - think about better way to push attachmentStorage down to serializer
        val bytes = run {
            val attachmentRef = importJar(storage)
            tx.addAttachment(storage.openAttachment(attachmentRef)!!.id)
            val wireTransaction = tx.toWireTransaction()
            wireTransaction.serialize(context = context)
        }
        val copiedWireTransaction = bytes.deserialize(context = context)
        assertEquals(1, copiedWireTransaction.outputs.size)
        // Contracts need to be loaded by the same classloader as the ContractState itself 
        val contractClassloader = copiedWireTransaction.getOutput(0).javaClass.classLoader
        val contract2 = contractClassloader.loadClass(copiedWireTransaction.outputs.first().contract).newInstance() as DummyContractBackdoor
        assertEquals(contract2.javaClass.classLoader, copiedWireTransaction.outputs[0].data.javaClass.classLoader)
        assertEquals(42, contract2.inspectState(copiedWireTransaction.outputs[0].data))
    }

    @Test
    fun `test deserialize of WireTransaction where contract cannot be found`() = kryoSpecific<AttachmentClassLoaderTests>("Kryo verifies/loads attachments on deserialization, whereas AMQP currently does not") {
        val child = ClassLoaderForTests()
        val contractClass = Class.forName(ISOLATED_CONTRACT_CLASS_NAME, true, child)
        val contract = contractClass.newInstance() as DummyContractBackdoor
        val tx = contract.generateInitial(MEGA_CORP.ref(0), 42, DUMMY_NOTARY)
        val storage = MockAttachmentStorage()

        // todo - think about better way to push attachmentStorage down to serializer
        val attachmentRef = importJar(storage)
        val bytes = run {

            tx.addAttachment(storage.openAttachment(attachmentRef)!!.id)

            val wireTransaction = tx.toWireTransaction()

            wireTransaction.serialize(context = SerializationFactory.defaultFactory.defaultContext.withAttachmentStorage(storage))
        }
        // use empty attachmentStorage

        val e = assertFailsWith(MissingAttachmentsException::class) {
            val mockAttStorage = MockAttachmentStorage()
            bytes.deserialize(context = SerializationFactory.defaultFactory.defaultContext.withAttachmentStorage(mockAttStorage))

            if(mockAttStorage.openAttachment(attachmentRef) == null) {
                throw MissingAttachmentsException(listOf(attachmentRef))
            }
        }
        assertEquals(attachmentRef, e.ids.single())
    }

    @Test
    fun `test loading a class from attachment during deserialization`() {
        val child = ClassLoaderForTests()
        val contractClass = Class.forName(ISOLATED_CONTRACT_CLASS_NAME, true, child)
        val contract = contractClass.newInstance() as DummyContractBackdoor
        val storage = MockAttachmentStorage()
        val attachmentRef = importJar(storage)
        val outboundContext = SerializationFactory.defaultFactory.defaultContext.withClassLoader(child)
        // We currently ignore annotations in attachments, so manually whitelist.
        val inboundContext = SerializationFactory.defaultFactory.defaultContext.withWhitelisted(contract.javaClass).withAttachmentStorage(storage).withAttachmentsClassLoader(listOf(attachmentRef))

        // Serialize with custom context to avoid populating the default context with the specially loaded class
        val serialized = contract.serialize(context = outboundContext)
        // Then deserialize with the attachment class loader associated with the attachment
        serialized.deserialize(context = inboundContext)
    }

    @Test
    fun `test loading a class with attachment missing during deserialization`() {
        val child = ClassLoaderForTests()
        val contractClass = Class.forName(ISOLATED_CONTRACT_CLASS_NAME, true, child)
        val contract = contractClass.newInstance() as DummyContractBackdoor
        val storage = MockAttachmentStorage()
        val attachmentRef = SecureHash.randomSHA256()
        val outboundContext = SerializationFactory.defaultFactory.defaultContext.withClassLoader(child)
        // Serialize with custom context to avoid populating the default context with the specially loaded class
        val serialized = contract.serialize(context = outboundContext)

        // Then deserialize with the attachment class loader associated with the attachment
        val e = assertFailsWith(MissingAttachmentsException::class) {
            // We currently ignore annotations in attachments, so manually whitelist.
            val inboundContext = SerializationFactory.defaultFactory.defaultContext.withWhitelisted(contract.javaClass).withAttachmentStorage(storage).withAttachmentsClassLoader(listOf(attachmentRef))
            serialized.deserialize(context = inboundContext)
        }
        assertEquals(attachmentRef, e.ids.single())
    }
}
