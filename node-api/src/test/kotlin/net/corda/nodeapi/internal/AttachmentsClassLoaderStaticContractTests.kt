package net.corda.nodeapi.internal

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.ServicesForResolution
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.node.internal.cordapp.CordappLoader
import net.corda.node.internal.cordapp.CordappProviderImpl
import net.corda.testing.*
import net.corda.testing.node.MockAttachmentStorage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AttachmentsClassLoaderStaticContractTests {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    class AttachmentDummyContract : Contract {
        companion object {
            private val ATTACHMENT_PROGRAM_ID = "net.corda.nodeapi.internal.AttachmentsClassLoaderStaticContractTests\$AttachmentDummyContract"
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

    private val serviceHub = rigorousMock<ServicesForResolution>().also {
        doReturn(CordappProviderImpl(CordappLoader.createWithTestPackages(listOf("net.corda.nodeapi.internal")), MockAttachmentStorage())).whenever(it).cordappProvider
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
        val contractClass = Class.forName("net.corda.nodeapi.internal.AttachmentsClassLoaderStaticContractTests\$AttachmentDummyContract")
        val contract = contractClass.newInstance() as Contract

        assertNotNull(contract)
    }
}