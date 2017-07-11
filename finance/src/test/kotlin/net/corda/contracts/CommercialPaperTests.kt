package net.corda.contracts

import net.corda.contracts.asset.*
import net.corda.testing.contracts.fillWithSomeTestCash
import net.corda.core.contracts.*
import net.corda.core.days
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.seconds
import net.corda.core.transactions.SignedTransaction
import net.corda.node.utilities.configureDatabase
import net.corda.testing.*
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestDataSourceProperties
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Instant
import java.util.*
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// TODO: The generate functions aren't tested by these tests: add them.

interface ICommercialPaperTestTemplate {
    fun getPaper(): ICommercialPaperState
    fun getIssueCommand(notary: Party): CommandData
    fun getRedeemCommand(notary: Party): CommandData
    fun getMoveCommand(): CommandData
}

class JavaCommercialPaperTest : ICommercialPaperTestTemplate {
    override fun getPaper(): ICommercialPaperState = JavaCommercialPaper.State(
            MEGA_CORP.ref(123),
            MEGA_CORP,
            1000.DOLLARS `issued by` MEGA_CORP.ref(123),
            TEST_TX_TIME + 7.days
    )

    override fun getIssueCommand(notary: Party): CommandData = JavaCommercialPaper.Commands.Issue()
    override fun getRedeemCommand(notary: Party): CommandData = JavaCommercialPaper.Commands.Redeem()
    override fun getMoveCommand(): CommandData = JavaCommercialPaper.Commands.Move()
}

class KotlinCommercialPaperTest : ICommercialPaperTestTemplate {
    override fun getPaper(): ICommercialPaperState = CommercialPaper.State(
            issuance = MEGA_CORP.ref(123),
            owner = MEGA_CORP,
            faceValue = 1000.DOLLARS `issued by` MEGA_CORP.ref(123),
            maturityDate = TEST_TX_TIME + 7.days
    )

    override fun getIssueCommand(notary: Party): CommandData = CommercialPaper.Commands.Issue()
    override fun getRedeemCommand(notary: Party): CommandData = CommercialPaper.Commands.Redeem()
    override fun getMoveCommand(): CommandData = CommercialPaper.Commands.Move()
}

class KotlinCommercialPaperLegacyTest : ICommercialPaperTestTemplate {
    override fun getPaper(): ICommercialPaperState = CommercialPaperLegacy.State(
            issuance = MEGA_CORP.ref(123),
            owner = MEGA_CORP,
            faceValue = 1000.DOLLARS `issued by` MEGA_CORP.ref(123),
            maturityDate = TEST_TX_TIME + 7.days
    )

    override fun getIssueCommand(notary: Party): CommandData = CommercialPaperLegacy.Commands.Issue()
    override fun getRedeemCommand(notary: Party): CommandData = CommercialPaperLegacy.Commands.Redeem()
    override fun getMoveCommand(): CommandData = CommercialPaperLegacy.Commands.Move()
}

@RunWith(Parameterized::class)
class CommercialPaperTestsGeneric {
    companion object {
        @Parameterized.Parameters @JvmStatic
        fun data() = listOf(JavaCommercialPaperTest(), KotlinCommercialPaperTest(), KotlinCommercialPaperLegacyTest())
    }

    @Parameterized.Parameter
    lateinit var thisTest: ICommercialPaperTestTemplate

    val issuer = MEGA_CORP.ref(123)

    @Test
    fun `trade lifecycle test`() {
        val someProfits = 1200.DOLLARS `issued by` issuer
        ledger {
            unverifiedTransaction {
                output("alice's $900", 900.DOLLARS.CASH `issued by` issuer `owned by` ALICE)
                output("some profits", someProfits.STATE `owned by` MEGA_CORP)
            }

            // Some CP is issued onto the ledger by MegaCorp.
            transaction("Issuance") {
                output("paper") { thisTest.getPaper() }
                command(MEGA_CORP_PUBKEY) { thisTest.getIssueCommand(DUMMY_NOTARY) }
                timeWindow(TEST_TX_TIME)
                this.verifies()
            }

            // The CP is sold to alice for her $900, $100 less than the face value. At 10% interest after only 7 days,
            // that sounds a bit too good to be true!
            transaction("Trade") {
                input("paper")
                input("alice's $900")
                output("borrowed $900") { 900.DOLLARS.CASH `issued by` issuer `owned by` MEGA_CORP }
                output("alice's paper") { "paper".output<ICommercialPaperState>() `owned by` ALICE }
                command(ALICE_PUBKEY) { Cash.Commands.Move() }
                command(MEGA_CORP_PUBKEY) { thisTest.getMoveCommand() }
                this.verifies()
            }

            // Time passes, and Alice redeem's her CP for $1000, netting a $100 profit. MegaCorp has received $1200
            // as a single payment from somewhere and uses it to pay Alice off, keeping the remaining $200 as change.
            transaction("Redemption") {
                input("alice's paper")
                input("some profits")

                fun TransactionDSL<TransactionDSLInterpreter>.outputs(aliceGetsBack: Amount<Issued<Currency>>) {
                    output("Alice's profit") { aliceGetsBack.STATE `owned by` ALICE }
                    output("Change") { (someProfits - aliceGetsBack).STATE `owned by` MEGA_CORP }
                }

                command(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
                command(ALICE_PUBKEY) { thisTest.getRedeemCommand(DUMMY_NOTARY) }

                tweak {
                    outputs(700.DOLLARS `issued by` issuer)
                    timeWindow(TEST_TX_TIME + 8.days)
                    this `fails with` "received amount equals the face value"
                }
                outputs(1000.DOLLARS `issued by` issuer)


                tweak {
                    timeWindow(TEST_TX_TIME + 2.days)
                    this `fails with` "must have matured"
                }
                timeWindow(TEST_TX_TIME + 8.days)

                tweak {
                    output { "paper".output<ICommercialPaperState>() }
                    this `fails with` "must be destroyed"
                }

                this.verifies()
            }
        }
    }

    @Test
    fun `key mismatch at issue`() {
        transaction {
            output { thisTest.getPaper() }
            command(DUMMY_PUBKEY_1) { thisTest.getIssueCommand(DUMMY_NOTARY) }
            timeWindow(TEST_TX_TIME)
            this `fails with` "output states are issued by a command signer"
        }
    }

    @Test
    fun `face value is not zero`() {
        transaction {
            output { thisTest.getPaper().withFaceValue(0.DOLLARS `issued by` issuer) }
            command(MEGA_CORP_PUBKEY) { thisTest.getIssueCommand(DUMMY_NOTARY) }
            timeWindow(TEST_TX_TIME)
            this `fails with` "output values sum to more than the inputs"
        }
    }

    @Test
    fun `maturity date not in the past`() {
        transaction {
            output { thisTest.getPaper().withMaturityDate(TEST_TX_TIME - 10.days) }
            command(MEGA_CORP_PUBKEY) { thisTest.getIssueCommand(DUMMY_NOTARY) }
            timeWindow(TEST_TX_TIME)
            this `fails with` "maturity date is not in the past"
        }
    }

    @Test
    fun `issue cannot replace an existing state`() {
        transaction {
            input(thisTest.getPaper())
            output { thisTest.getPaper() }
            command(MEGA_CORP_PUBKEY) { thisTest.getIssueCommand(DUMMY_NOTARY) }
            timeWindow(TEST_TX_TIME)
            this `fails with` "output values sum to more than the inputs"
        }
    }

    /**
     *  Unit test requires two separate Database instances to represent each of the two
     *  transaction participants (enforces uniqueness of vault content in lieu of partipant identity)
     */

    private lateinit var bigCorpServices: MockServices
    private lateinit var bigCorpVault: Vault<ContractState>
    private lateinit var bigCorpVaultService: VaultService

    private lateinit var aliceServices: MockServices
    private lateinit var aliceVaultService: VaultService
    private lateinit var alicesVault: Vault<ContractState>

    private val notaryServices = MockServices(DUMMY_NOTARY_KEY)

    private lateinit var moveTX: SignedTransaction

    @Test
    fun `issue move and then redeem`() {

        val dataSourcePropsAlice = makeTestDataSourceProperties()
        val databaseAlice = configureDatabase(dataSourcePropsAlice)
        databaseAlice.transaction {

            aliceServices = object : MockServices(ALICE_KEY) {
                override val vaultService: VaultService = makeVaultService(dataSourcePropsAlice)

                override fun recordTransactions(txs: Iterable<SignedTransaction>) {
                    for (stx in txs) {
                        validatedTransactions.addTransaction(stx)
                    }
                    // Refactored to use notifyAll() as we have no other unit test for that method with multiple transactions.
                    vaultService.notifyAll(txs.map { it.tx })
                }
            }
            alicesVault = aliceServices.fillWithSomeTestCash(9000.DOLLARS, atLeastThisManyStates = 1, atMostThisManyStates = 1)
            aliceVaultService = aliceServices.vaultService
        }

        val dataSourcePropsBigCorp = makeTestDataSourceProperties()
        val databaseBigCorp = configureDatabase(dataSourcePropsBigCorp)
        databaseBigCorp.transaction {

            bigCorpServices = object : MockServices(BIG_CORP_KEY) {
                override val vaultService: VaultService = makeVaultService(dataSourcePropsBigCorp)

                override fun recordTransactions(txs: Iterable<SignedTransaction>) {
                    for (stx in txs) {
                        validatedTransactions.addTransaction(stx)
                    }
                    // Refactored to use notifyAll() as we have no other unit test for that method with multiple transactions.
                    vaultService.notifyAll(txs.map { it.tx })
                }
            }
            bigCorpVault = bigCorpServices.fillWithSomeTestCash(13000.DOLLARS, atLeastThisManyStates = 1, atMostThisManyStates = 1)
            bigCorpVaultService = bigCorpServices.vaultService
        }

        // Propagate the cash transactions to each side.
        aliceServices.recordTransactions(bigCorpVault.states.map { bigCorpServices.validatedTransactions.getTransaction(it.ref.txhash)!! })
        bigCorpServices.recordTransactions(alicesVault.states.map { aliceServices.validatedTransactions.getTransaction(it.ref.txhash)!! })

        // BigCorp™ issues $10,000 of commercial paper, to mature in 30 days, owned initially by itself.
        val faceValue = 10000.DOLLARS `issued by` DUMMY_CASH_ISSUER
        val issuance = bigCorpServices.myInfo.legalIdentity.ref(1)
        val issueBuilder = CommercialPaper().generateIssue(issuance, faceValue, TEST_TX_TIME + 30.days, DUMMY_NOTARY)
        issueBuilder.setTimeWindow(TEST_TX_TIME, 30.seconds)
        val issuePtx = bigCorpServices.signInitialTransaction(issueBuilder)
        val issueTx = notaryServices.addSignature(issuePtx)

        databaseAlice.transaction {
            // Alice pays $9000 to BigCorp to own some of their debt.
            moveTX = run {
                val builder = TransactionType.General.Builder(DUMMY_NOTARY)
                aliceVaultService.generateSpend(builder, 9000.DOLLARS, AnonymousParty(bigCorpServices.key.public))
                CommercialPaper().generateMove(builder, issueTx.tx.outRef(0), AnonymousParty(aliceServices.key.public))
                val ptx = aliceServices.signInitialTransaction(builder)
                val ptx2 = bigCorpServices.addSignature(ptx)
                val stx = notaryServices.addSignature(ptx2)
                stx
            }
        }

        databaseBigCorp.transaction {
            // Verify the txns are valid and insert into both sides.
            listOf(issueTx, moveTX).forEach {
                it.toLedgerTransaction(aliceServices).verify()
                aliceServices.recordTransactions(it)
                bigCorpServices.recordTransactions(it)
            }
        }

        databaseBigCorp.transaction {
            fun makeRedeemTX(time: Instant): Pair<SignedTransaction, UUID> {
                val builder = TransactionType.General.Builder(DUMMY_NOTARY)
                builder.setTimeWindow(time, 30.seconds)
                CommercialPaper().generateRedeem(builder, moveTX.tx.outRef(1), bigCorpVaultService)
                val ptx = aliceServices.signInitialTransaction(builder)
                val ptx2 = bigCorpServices.addSignature(ptx)
                val stx = notaryServices.addSignature(ptx2)
                return Pair(stx, builder.lockId)
            }

            val redeemTX = makeRedeemTX(TEST_TX_TIME + 10.days)
            val tooEarlyRedemption = redeemTX.first
            val tooEarlyRedemptionLockId = redeemTX.second
            val e = assertFailsWith(TransactionVerificationException::class) {
                tooEarlyRedemption.toLedgerTransaction(aliceServices).verify()
            }
            // manually release locks held by this failing transaction
            aliceServices.vaultService.softLockRelease(tooEarlyRedemptionLockId)
            assertTrue(e.cause!!.message!!.contains("paper must have matured"))

            val validRedemption = makeRedeemTX(TEST_TX_TIME + 31.days).first
            validRedemption.toLedgerTransaction(aliceServices).verify()
            // soft lock not released after success either!!! (as transaction not recorded)
        }
    }
}
