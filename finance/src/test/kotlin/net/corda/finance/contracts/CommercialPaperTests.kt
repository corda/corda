package net.corda.finance.contracts

import net.corda.core.contracts.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.days
import net.corda.core.utilities.seconds
import net.corda.finance.DOLLARS
import net.corda.finance.`issued by`
import net.corda.finance.contracts.asset.CASH
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.asset.STATE
import net.corda.testing.core.*
import net.corda.testing.dsl.EnforceVerifyOrFail
import net.corda.testing.dsl.TransactionDSL
import net.corda.testing.dsl.TransactionDSLInterpreter
import net.corda.testing.internal.TEST_TX_TIME
import net.corda.testing.internal.vault.VaultFiller
import net.corda.testing.node.MockServices
import net.corda.testing.node.MockServices.Companion.makeTestDatabaseAndMockServices
import net.corda.testing.node.ledger
import net.corda.testing.node.makeTestIdentityService
import net.corda.testing.node.transaction
import org.junit.Rule
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
    fun getContract(): ContractClassName
}

private val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))

class JavaCommercialPaperTest : ICommercialPaperTestTemplate {
    override fun getPaper(): ICommercialPaperState = JavaCommercialPaper.State(
            megaCorp.ref(123),
            megaCorp.party,
            1000.DOLLARS `issued by` megaCorp.ref(123),
            TEST_TX_TIME + 7.days
    )

    override fun getIssueCommand(notary: Party): CommandData = JavaCommercialPaper.Commands.Issue()
    override fun getRedeemCommand(notary: Party): CommandData = JavaCommercialPaper.Commands.Redeem()
    override fun getMoveCommand(): CommandData = JavaCommercialPaper.Commands.Move()
    override fun getContract() = JavaCommercialPaper.JCP_PROGRAM_ID
}

class KotlinCommercialPaperTest : ICommercialPaperTestTemplate {
    override fun getPaper(): ICommercialPaperState = CommercialPaper.State(
            issuance = megaCorp.ref(123),
            owner = megaCorp.party,
            faceValue = 1000.DOLLARS `issued by` megaCorp.ref(123),
            maturityDate = TEST_TX_TIME + 7.days
    )

    override fun getIssueCommand(notary: Party): CommandData = CommercialPaper.Commands.Issue()
    override fun getRedeemCommand(notary: Party): CommandData = CommercialPaper.Commands.Redeem()
    override fun getMoveCommand(): CommandData = CommercialPaper.Commands.Move()
    override fun getContract() = CommercialPaper.CP_PROGRAM_ID
}

class KotlinCommercialPaperLegacyTest : ICommercialPaperTestTemplate {
    override fun getPaper(): ICommercialPaperState = CommercialPaper.State(
            issuance = megaCorp.ref(123),
            owner = megaCorp.party,
            faceValue = 1000.DOLLARS `issued by` megaCorp.ref(123),
            maturityDate = TEST_TX_TIME + 7.days
    )

    override fun getIssueCommand(notary: Party): CommandData = CommercialPaper.Commands.Issue()
    override fun getRedeemCommand(notary: Party): CommandData = CommercialPaper.Commands.Redeem()
    override fun getMoveCommand(): CommandData = CommercialPaper.Commands.Move()
    override fun getContract() = CommercialPaper.CP_PROGRAM_ID
}

@RunWith(Parameterized::class)
class CommercialPaperTestsGeneric {
    companion object {
        @Parameterized.Parameters
        @JvmStatic
        fun data() = listOf(JavaCommercialPaperTest(), KotlinCommercialPaperTest(), KotlinCommercialPaperLegacyTest())

        private val dummyCashIssuer = TestIdentity(CordaX500Name("Snake Oil Issuer", "London", "GB"), 10)
        private val dummyNotary = TestIdentity(DUMMY_NOTARY_NAME, 20)
        private val alice = TestIdentity(ALICE_NAME, 70)
        private val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "London", "GB"))
    }

    @Parameterized.Parameter
    lateinit var thisTest: ICommercialPaperTestTemplate

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val megaCorpRef = megaCorp.ref(123)
    private val ledgerServices = MockServices(listOf("net.corda.finance.schemas"), megaCorp, miniCorp)

    @Test
    fun `trade lifecycle test`() {
        val someProfits = 1200.DOLLARS `issued by` megaCorpRef
        ledgerServices.ledger(dummyNotary.party) {
            unverifiedTransaction {
                attachment(Cash.PROGRAM_ID)
                output(Cash.PROGRAM_ID, "alice's $900", 900.DOLLARS.CASH issuedBy megaCorpRef ownedBy alice.party)
                output(Cash.PROGRAM_ID, "some profits", someProfits.STATE ownedBy megaCorp.party)
            }

            // Some CP is issued onto the ledger by MegaCorp.
            transaction("Issuance") {
                attachments(CP_PROGRAM_ID, JavaCommercialPaper.JCP_PROGRAM_ID)
                output(thisTest.getContract(), "paper", thisTest.getPaper())
                command(megaCorp.publicKey, thisTest.getIssueCommand(dummyNotary.party))
                timeWindow(TEST_TX_TIME)
                this.verifies()
            }

            // The CP is sold to alice for her $900, $100 less than the face value. At 10% interest after only 7 days,
            // that sounds a bit too good to be true!
            transaction("Trade") {
                attachments(Cash.PROGRAM_ID, JavaCommercialPaper.JCP_PROGRAM_ID)
                input("paper")
                input("alice's $900")
                output(Cash.PROGRAM_ID, "borrowed $900", 900.DOLLARS.CASH issuedBy megaCorpRef ownedBy megaCorp.party)
                output(thisTest.getContract(), "alice's paper", "paper".output<ICommercialPaperState>().withOwner(alice.party))
                command(alice.publicKey, Cash.Commands.Move())
                command(megaCorp.publicKey, thisTest.getMoveCommand())
                this.verifies()
            }

            // Time passes, and Alice redeem's her CP for $1000, netting a $100 profit. MegaCorp has received $1200
            // as a single payment from somewhere and uses it to pay Alice off, keeping the remaining $200 as change.
            transaction("Redemption") {
                attachments(CP_PROGRAM_ID, JavaCommercialPaper.JCP_PROGRAM_ID)
                input("alice's paper")
                input("some profits")

                fun TransactionDSL<TransactionDSLInterpreter>.outputs(aliceGetsBack: Amount<Issued<Currency>>) {
                    output(Cash.PROGRAM_ID, "Alice's profit", aliceGetsBack.STATE ownedBy alice.party)
                    output(Cash.PROGRAM_ID, "Change", (someProfits - aliceGetsBack).STATE ownedBy megaCorp.party)
                }
                command(megaCorp.publicKey, Cash.Commands.Move())
                command(alice.publicKey, thisTest.getRedeemCommand(dummyNotary.party))
                tweak {
                    outputs(700.DOLLARS `issued by` megaCorpRef)
                    timeWindow(TEST_TX_TIME + 8.days)
                    this `fails with` "received amount equals the face value"
                }
                outputs(1000.DOLLARS `issued by` megaCorpRef)


                tweak {
                    timeWindow(TEST_TX_TIME + 2.days)
                    this `fails with` "must have matured"
                }
                timeWindow(TEST_TX_TIME + 8.days)

                tweak {
                    output(thisTest.getContract(), "paper".output<ICommercialPaperState>())
                    this `fails with` "must be destroyed"
                }

                this.verifies()
            }
        }
    }

    private fun transaction(script: TransactionDSL<TransactionDSLInterpreter>.() -> EnforceVerifyOrFail) = run {
        ledgerServices.transaction(dummyNotary.party, script)
    }

    @Test
    fun `key mismatch at issue`() {
        transaction {
            attachment(CP_PROGRAM_ID)
            attachment(JavaCommercialPaper.JCP_PROGRAM_ID)
            output(thisTest.getContract(), thisTest.getPaper())
            command(miniCorp.publicKey, thisTest.getIssueCommand(dummyNotary.party))
            timeWindow(TEST_TX_TIME)
            this `fails with` "output states are issued by a command signer"
        }
    }

    @Test
    fun `face value is not zero`() {
        transaction {
            attachment(CP_PROGRAM_ID)
            attachment(JavaCommercialPaper.JCP_PROGRAM_ID)
            output(thisTest.getContract(), thisTest.getPaper().withFaceValue(0.DOLLARS `issued by` megaCorpRef))
            command(megaCorp.publicKey, thisTest.getIssueCommand(dummyNotary.party))
            timeWindow(TEST_TX_TIME)
            this `fails with` "output values sum to more than the inputs"
        }
    }

    @Test
    fun `maturity date not in the past`() {
        transaction {
            attachment(CP_PROGRAM_ID)
            attachment(JavaCommercialPaper.JCP_PROGRAM_ID)
            output(thisTest.getContract(), thisTest.getPaper().withMaturityDate(TEST_TX_TIME - 10.days))
            command(megaCorp.publicKey, thisTest.getIssueCommand(dummyNotary.party))
            timeWindow(TEST_TX_TIME)
            this `fails with` "maturity date is not in the past"
        }
    }

    @Test
    fun `issue cannot replace an existing state`() {
        transaction {
            attachment(CP_PROGRAM_ID)
            attachment(JavaCommercialPaper.JCP_PROGRAM_ID)
            input(thisTest.getContract(), thisTest.getPaper())
            output(thisTest.getContract(), thisTest.getPaper())
            command(megaCorp.publicKey, thisTest.getIssueCommand(dummyNotary.party))
            timeWindow(TEST_TX_TIME)
            this `fails with` "output values sum to more than the inputs"
        }
    }

    @Test
    fun `issue move and then redeem`() {
        // Set up a test environment with 4 parties:
        // 1. The notary
        // 2. A dummy cash issuer e.g. central bank
        // 3. Alice
        // 4. MegaCorp
        //
        // MegaCorp will issue some commercial paper and Alice will buy it, using cash issued to her in the name
        // of the dummy cash issuer.

        val allIdentities = arrayOf(megaCorp.identity, alice.identity, dummyCashIssuer.identity, dummyNotary.identity)
        val notaryServices = MockServices(listOf("net.corda.finance.contracts", "net.corda.finance.contracts.asset", "net.corda.finance.schemas"), dummyNotary)
        val issuerServices = MockServices(listOf("net.corda.finance.contracts", "net.corda.finance.contracts.asset", "net.corda.finance.schemas"), dummyCashIssuer, dummyNotary)
        val (aliceDatabase, aliceServices) = makeTestDatabaseAndMockServices(
                listOf("net.corda.finance.contracts", "net.corda.finance.schemas"),
                makeTestIdentityService(*allIdentities),
                alice
        )
        val aliceCash: Vault<Cash.State> = aliceDatabase.transaction {
            VaultFiller(aliceServices, dummyNotary).fillWithSomeTestCash(9000.DOLLARS, issuerServices, 1, dummyCashIssuer.ref(1))
        }

        val (megaCorpDatabase, megaCorpServices) = makeTestDatabaseAndMockServices(
                listOf("net.corda.finance.contracts", "net.corda.finance.schemas"),
                makeTestIdentityService(*allIdentities),
                megaCorp
        )
        val bigCorpCash: Vault<Cash.State> = megaCorpDatabase.transaction {
             VaultFiller(megaCorpServices, dummyNotary).fillWithSomeTestCash(13000.DOLLARS, issuerServices, 1, dummyCashIssuer.ref(1))
        }

        // Propagate the cash transactions to each side.
        aliceServices.recordTransactions(bigCorpCash.states.map { megaCorpServices.validatedTransactions.getTransaction(it.ref.txhash)!! })
        megaCorpServices.recordTransactions(aliceCash.states.map { aliceServices.validatedTransactions.getTransaction(it.ref.txhash)!! })

        // MegaCorp™ issues $10,000 of commercial paper, to mature in 30 days, owned initially by itself.
        val faceValue = 10000.DOLLARS `issued by` dummyCashIssuer.ref(1)
        val issuance = megaCorpServices.myInfo.singleIdentity().ref(1)
        val issueBuilder = CommercialPaper().generateIssue(issuance, faceValue, TEST_TX_TIME + 30.days, dummyNotary.party)
        issueBuilder.setTimeWindow(TEST_TX_TIME, 30.seconds)
        val issuePtx = megaCorpServices.signInitialTransaction(issueBuilder)
        val issueTx = notaryServices.addSignature(issuePtx)

        val moveTX = aliceDatabase.transaction {
            // Alice pays $9000 to BigCorp to own some of their debt.
            val builder = TransactionBuilder(dummyNotary.party)
            Cash.generateSpend(aliceServices, builder, 9000.DOLLARS, alice.identity, AnonymousParty(megaCorp.publicKey))
            CommercialPaper().generateMove(builder, issueTx.tx.outRef(0), AnonymousParty(alice.keyPair.public))
            val ptx = aliceServices.signInitialTransaction(builder)
            val ptx2 = megaCorpServices.addSignature(ptx)
            val stx = notaryServices.addSignature(ptx2)
            stx
        }

        megaCorpDatabase.transaction {
            // Verify the txns are valid and insert into both sides.
            for (tx in listOf(issueTx, moveTX)) {
                tx.toLedgerTransaction(aliceServices).verify()
                aliceServices.recordTransactions(tx)
                megaCorpServices.recordTransactions(tx)
            }
        }

        megaCorpDatabase.transaction {
            fun makeRedeemTX(time: Instant): Pair<SignedTransaction, UUID> {
                val builder = TransactionBuilder(dummyNotary.party)
                builder.setTimeWindow(time, 30.seconds)
                CommercialPaper().generateRedeem(builder, moveTX.tx.outRef(1), megaCorpServices, megaCorpServices.myInfo.singleIdentityAndCert())
                val ptx = aliceServices.signInitialTransaction(builder)
                val ptx2 = megaCorpServices.addSignature(ptx)
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
            assertTrue("paper must have matured" in e.cause!!.message!!)

            val validRedemption = makeRedeemTX(TEST_TX_TIME + 31.days).first
            validRedemption.toLedgerTransaction(aliceServices).verify()
            // soft lock not released after success either!!! (as transaction not recorded)
        }
    }
}
