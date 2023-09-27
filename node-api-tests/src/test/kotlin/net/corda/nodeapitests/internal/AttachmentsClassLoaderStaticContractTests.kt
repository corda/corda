package net.corda.nodeapitests.internal

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.nodeapitests.internal.AttachmentsClassLoaderStaticContractTests.AttachmentDummyContract.Companion.ATTACHMENT_PROGRAM_ID
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
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
            const val ATTACHMENT_PROGRAM_ID = "net.corda.nodeapitests.internal.AttachmentsClassLoaderStaticContractTests\$AttachmentDummyContract"
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

    private val serviceHub = MockServices()

    @Test(timeout=300_000)
	fun `test serialization of WireTransaction with statically loaded contract`() {
        val tx = AttachmentDummyContract()
                .generateInitial(MEGA_CORP.ref(0), 42, DUMMY_NOTARY)
        val wireTransaction = tx.toWireTransaction(serviceHub)
        val bytes = wireTransaction.serialize()
        val copiedWireTransaction = bytes.deserialize()

        assertEquals(1, copiedWireTransaction.outputs.size)
        assertEquals(42, (copiedWireTransaction.outputs[0].data as AttachmentDummyContract.State).magicNumber)
    }

    @Test(timeout=300_000)
	fun `verify that contract DummyContract is in classPath`() {
        val contractClass = Class.forName(ATTACHMENT_PROGRAM_ID)
        assertThat(contractClass.getDeclaredConstructor().newInstance()).isInstanceOf(Contract::class.java)
    }
}
