package net.corda.coretests.solana
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractState
import net.corda.core.crypto.generateKeyPair
import net.corda.core.crypto.secureRandomBytes
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NotaryInfo
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.solana.AccountMeta
import net.corda.core.solana.Pubkey
import net.corda.core.solana.SolanaInstruction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestIdentityService
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.random.Random

class SolanaInstructionTest {
    companion object {
        private val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val keyPair = generateKeyPair()
    private val services = MockServices(
            listOf("net.corda.testing.contracts"),
            TestIdentity(CordaX500Name("MegaCorp", "London", "GB"), keyPair),
            makeTestIdentityService(),
            testNetworkParameters(notaries = listOf(NotaryInfo(DUMMY_NOTARY, true))),
            keyPair
    )
    private val identity: Party = services.myInfo.singleIdentity()

    @Before
    fun setup() {
        services.addMockCordapp(DummyContract.PROGRAM_ID)
    }

    @Test(timeout=300_000)
    fun `build transaction with solana instruction`() {
        val instruction = SolanaInstruction(
                randomPubkey(),
                listOf(
                        AccountMeta(randomPubkey(), isSigner = false, isWritable = true),
                        AccountMeta(randomPubkey(), isSigner = false, isWritable = true),
                        AccountMeta(randomPubkey(), isSigner = true, isWritable = false),
                ),
                OpaqueBytes(Random.nextBytes(9))
        )

        val wtx = TransactionBuilder(notary = DUMMY_NOTARY)
                .addOutputState(DummyState("dummy"), DummyContract.PROGRAM_ID)
                .addCommand(DummyCommand("cmd"), listOf(identity.owningKey))
                .addNotaryInstruction(instruction)
                .toWireTransaction(services)
        val wtxRoundtrip = wtx.serialize().deserialize()
        val ltx = wtxRoundtrip.toLedgerTransaction(services)
        assertThat(ltx.notaryInstructions).containsOnly(instruction)
        assertThat(ltx.notaryInstructions[0]).isNotSameAs(instruction)
    }

    private fun randomPubkey(): Pubkey = Pubkey(secureRandomBytes(32))

    @BelongsToContract(DummyContract::class)
    private data class DummyState(val data: String) : ContractState {
        override val participants: List<AbstractParty> = emptyList()
    }

    private data class DummyCommand(val data: String) : CommandData
}
