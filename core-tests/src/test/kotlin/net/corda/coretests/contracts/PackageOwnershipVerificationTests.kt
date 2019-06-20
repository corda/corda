package net.corda.coretests.contracts

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.*
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
        const val DUMMY_CONTRACT = "net.corda.coretests.contracts.DummyContract"
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
                    packageOwnership = mapOf(
                            "net.corda.coretests.contracts" to OWNER_KEY_PAIR.public,
                            "net.corda.isolated.workflows" to BOB_PUBKEY
                    ),
                    notaries = listOf(NotaryInfo(DUMMY_NOTARY, true))
            )
    )

    @Test
    fun `Happy path - Transaction validates when package signed by owner`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            transaction {
                attachment(DUMMY_CONTRACT, SecureHash.allOnesHash, listOf(OWNER_KEY_PAIR.public))
                output(DUMMY_CONTRACT, "c1", DUMMY_NOTARY, null, HashAttachmentConstraint(SecureHash.allOnesHash), DummyContractState())
                command(ALICE_PUBKEY, DummyIssue())
                verifies()
            }
        }
    }

    @Test
    fun `Transaction validation fails when the selected attachment is not signed by the owner`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            transaction {
                attachment(DUMMY_CONTRACT, SecureHash.allOnesHash, listOf(ALICE_PUBKEY))
                output(DUMMY_CONTRACT, "c1", DUMMY_NOTARY, null, HashAttachmentConstraint(SecureHash.allOnesHash), DummyContractState())
                command(ALICE_PUBKEY, DummyIssue())
                failsWith("is not signed by the owner")
            }
        }
    }

    @Test
    fun `packages that do not have contracts in are still ownable`() {
        // The first version of this feature was incorrectly concerned with contract classes and only contract
        // classes, but for the feature to work it must apply to any package. This tests that by using a package
        // in isolated.jar that doesn't include any contracts.
        ledgerServices.ledger(DUMMY_NOTARY) {
            transaction {
                attachment(DUMMY_CONTRACT, SecureHash.allOnesHash, listOf(OWNER_KEY_PAIR.public))
                attachment(attachment(javaClass.getResourceAsStream("/isolated.jar")))
                output(DUMMY_CONTRACT, "c1", DUMMY_NOTARY, null, HashAttachmentConstraint(SecureHash.allOnesHash), DummyContractState())
                command(ALICE_PUBKEY, DummyIssue())
                failsWith("is not signed by the owner")
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