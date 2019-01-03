package net.corda.core.contracts

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.NotaryInfo
import net.corda.core.transactions.LedgerTransaction
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Rule
import org.junit.Test

class PackageOwnershipVerificationTests {

    private companion object {
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
        val ALICE = TestIdentity(CordaX500Name("ALICE", "London", "GB"))
        val ALICE_PARTY get() = ALICE.party
        val ALICE_PUBKEY get() = ALICE.publicKey
        val BOB = TestIdentity(CordaX500Name("BOB", "London", "GB"))
        val BOB_PARTY get() = BOB.party
        val BOB_PUBKEY get() = BOB.publicKey
        val dummyContract = "net.corda.core.contracts.DummyContract"
        val OWNER_KEY_PAIR = Crypto.generateKeyPair()
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val ledgerServices = MockServices(
            cordappPackages = listOf("net.corda.finance.contracts.asset"),
            initialIdentity = ALICE,
            identityService = mock<IdentityServiceInternal>().also {
                doReturn(ALICE_PARTY).whenever(it).partyFromKey(ALICE_PUBKEY)
                doReturn(BOB_PARTY).whenever(it).partyFromKey(BOB_PUBKEY)
            },
            networkParameters = testNetworkParameters(
                    packageOwnership = mapOf("net.corda.core.contracts" to OWNER_KEY_PAIR.public),
                    notaries = listOf(NotaryInfo(DUMMY_NOTARY, true))
            )
    )

    @Test
    fun `Happy path - Transaction validates when package signed by owner`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            transaction {
                attachment(dummyContract, SecureHash.allOnesHash, listOf(OWNER_KEY_PAIR.public))
                output(dummyContract, "c1", DUMMY_NOTARY, null, HashAttachmentConstraint(SecureHash.allOnesHash), DummyContractState())
                command(ALICE_PUBKEY, DummyIssue())
                verifies()
            }
        }
    }

    @Test
    fun `Transaction validation fails when the selected attachment is not signed by the owner`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            transaction {
                attachment(dummyContract, SecureHash.allOnesHash, listOf(ALICE_PUBKEY))
                output(dummyContract, "c1", DUMMY_NOTARY, null, HashAttachmentConstraint(SecureHash.allOnesHash), DummyContractState())
                command(ALICE_PUBKEY, DummyIssue())
                failsWith("is not signed by the owner specified in the network parameters")
            }
        }
    }
}

@BelongsToContract(DummyContract::class)
class DummyContractState : ContractState {
    override val participants: List<AbstractParty>
        get() = emptyList()
}

class DummyContract : Contract {
    interface Commands : CommandData
    class Create : Commands

    override fun verify(tx: LedgerTransaction) {
        //do nothing
    }
}

class DummyIssue : TypeOnlyCommandData()