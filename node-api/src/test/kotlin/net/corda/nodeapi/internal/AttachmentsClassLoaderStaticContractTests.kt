package net.corda.nodeapi.internal

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.node.services.NetworkParametersService
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.nodeapi.internal.cordapp.CordappLoader
import net.corda.node.internal.cordapp.CordappProviderImpl
import net.corda.node.internal.cordapp.JarScanningCordappLoader
import net.corda.nodeapi.internal.AttachmentsClassLoaderStaticContractTests.AttachmentDummyContract.Companion.ATTACHMENT_PROGRAM_ID
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.MockCordappConfigProvider
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.internal.cordappWithPackages
import net.corda.testing.services.MockAttachmentStorage
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AttachmentsClassLoaderStaticContractTests {
    private companion object {
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
        val MEGA_CORP = TestIdentity(CordaX500Name("MegaCorp", "London", "GB")).party
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    class AttachmentDummyContract : Contract {
        companion object {
            const val ATTACHMENT_PROGRAM_ID = "net.corda.nodeapi.internal.AttachmentsClassLoaderStaticContractTests\$AttachmentDummyContract"
        }

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
            return TransactionBuilder(notary)
                    .withItems(StateAndContract(state, ATTACHMENT_PROGRAM_ID), Command(Commands.Create(), owner.party.owningKey))
        }
    }

    private val networkParameters = testNetworkParameters()

    private val networkParametersService get() = mock<NetworkParametersService>().also {
        doReturn(networkParameters.serialize().hash).whenever(it).currentHash
    }

    private val serviceHub get() = rigorousMock<ServicesForResolution>().also {
        val cordappProviderImpl = CordappProviderImpl(cordappLoaderForPackages(listOf("net.corda.nodeapi.internal")), MockCordappConfigProvider(), MockAttachmentStorage())
        cordappProviderImpl.start()
        doReturn(cordappProviderImpl).whenever(it).cordappProvider
        doReturn(networkParametersService).whenever(it).networkParametersService
        doReturn(networkParameters).whenever(it).networkParameters
        val attachmentStorage = rigorousMock<AttachmentStorage>()
        doReturn(attachmentStorage).whenever(it).attachments
        val attachment = rigorousMock<ContractAttachment>()
        doReturn(attachment).whenever(attachmentStorage).openAttachment(any())
        doReturn(it.cordappProvider.getContractAttachmentID(AttachmentDummyContract.ATTACHMENT_PROGRAM_ID)).whenever(attachment).id
        doReturn(setOf(AttachmentDummyContract.ATTACHMENT_PROGRAM_ID)).whenever(attachment).allContracts
        doReturn("app").whenever(attachment).uploader
        doReturn(emptyList<Party>()).whenever(attachment).signerKeys
        val contractAttachmentId = SecureHash.randomSHA256()
        doReturn(listOf(contractAttachmentId)).whenever(attachmentStorage)
                .getLatestContractAttachments(AttachmentDummyContract.ATTACHMENT_PROGRAM_ID)
    }

    @Test
    fun `test serialization of WireTransaction with statically loaded contract`() {
        val tx = AttachmentDummyContract().generateInitial(MEGA_CORP.ref(0), 42, DUMMY_NOTARY)
        val wireTransaction = tx.toWireTransaction(serviceHub)
        val bytes = wireTransaction.serialize()
        val copiedWireTransaction = bytes.deserialize()

        assertEquals(1, copiedWireTransaction.outputs.size)
        assertEquals(42, (copiedWireTransaction.outputs[0].data as AttachmentDummyContract.State).magicNumber)
    }

    @Test
    fun `verify that contract DummyContract is in classPath`() {
        val contractClass = Class.forName(ATTACHMENT_PROGRAM_ID)
        assertThat(contractClass.newInstance()).isInstanceOf(Contract::class.java)
    }

    private fun cordappLoaderForPackages(packages: Collection<String>): CordappLoader {
        return JarScanningCordappLoader.fromJarUrls(listOf(cordappWithPackages(*packages.toTypedArray()).jarFile.toUri().toURL()))
    }
}
