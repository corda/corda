package contracts

import core.*
import core.testutils.*
import org.junit.Test
import java.util.*

class CrowdFundTests {
    val CF_1 = CrowdFund.State(
            owner = MINI_CORP_PUBKEY,
            fundingName = "kickstart me",
            fundingTarget = 1000.DOLLARS,
            pledgeTotal = 0.DOLLARS,
            pledgeCount = 0,
            closingTime = TEST_TX_TIME + 7.days,
            closed = false,
            pledges = ArrayList<CrowdFund.Pledge>()
    )

    @Test
    fun `key mismatch at issue`() {
        transactionGroup {
            transaction {
                output { CF_1 }
                arg(DUMMY_PUBKEY_1) { CrowdFund.Commands.Register }
            }

            expectFailureOfTx(1, "the transaction is signed by the owner of the crowdsourcing")
        }
    }

    @Test
    fun `closing time not in the future`() {
        transactionGroup {
            transaction {
                output { CF_1.copy(closingTime = TEST_TX_TIME - 1.days) }
                arg(MINI_CORP_PUBKEY) { CrowdFund.Commands.Register }
            }

            expectFailureOfTx(1, "the output registration has a closing time in the future")
        }
    }

    @Test
    fun ok() {
        raiseFunds().verify()
    }

    private fun raiseFunds(): TransactionGroupForTest<CrowdFund.State> {
        return transactionGroupFor<CrowdFund.State> {
            roots {
                transaction(1000.DOLLARS.CASH `owned by` ALICE label "alice's $1000")
            }

            // 1. Create the funding opportunity
            transaction {
                output("funding opportunity") { CF_1 }
                arg(MINI_CORP_PUBKEY) { CrowdFund.Commands.Register }
            }

            // 2. Place a pledge
            transaction {
                input ("funding opportunity")
                input("alice's $1000")
                output ("pledged opportunity") { CF_1.copy(
                        pledges = CF_1.pledges + CrowdFund.Pledge(ALICE, 1000.DOLLARS),
                        pledgeCount = CF_1.pledgeCount + 1,
                        pledgeTotal = CF_1.pledgeTotal + 1000.DOLLARS
                ) }
                output { 1000.DOLLARS.CASH `owned by` MINI_CORP_PUBKEY }
                arg(ALICE) { Cash.Commands.Move() }
                arg(ALICE) { CrowdFund.Commands.Fund }
            }

            // 3. Close the opportunity, assuming the target has been met
            transaction(TEST_TX_TIME + 8.days) {
                input ("pledged opportunity")
                output ("funded and closed") { "pledged opportunity".output.copy(closed = true) }
                arg(MINI_CORP_PUBKEY) { CrowdFund.Commands.Funded }
            }
         }
    }

    fun cashOutputsToWallet(vararg states: Cash.State): Pair<LedgerTransaction, List<StateAndRef<Cash.State>>> {
        val ltx = LedgerTransaction(emptyList(), listOf(*states), emptyList(), TEST_TX_TIME, SecureHash.randomSHA256())
        return Pair(ltx, states.mapIndexed { index, state -> StateAndRef(state, ContractStateRef(ltx.hash, index)) })
    }

    @Test
    fun `raise more funds`() {
        // MiniCorp registers a crowdfunding of $1,000, to close in 30 days.
        val registerTX: LedgerTransaction = run {
            // craftRegister returns a partial transaction
            val ptx = CrowdFund().craftRegister(MINI_CORP.ref(123), 1000.DOLLARS, "crowd funding", TEST_TX_TIME + 7.days)
            ptx.signWith(MINI_CORP_KEY)
            val stx = ptx.toSignedTransaction()
            stx.verify().toLedgerTransaction(TEST_TX_TIME, TEST_KEYS_TO_CORP_MAP, SecureHash.randomSHA256())
        }

        // let's give Alice some funds that she can invest
        val (aliceWalletTX, aliceWallet) = cashOutputsToWallet(
                200.DOLLARS.CASH `owned by` ALICE,
                500.DOLLARS.CASH `owned by` ALICE,
                300.DOLLARS.CASH `owned by` ALICE
        )

        // Alice pays $1000 to MiniCorp to fund their campaign.
        val pledgeTX: LedgerTransaction = run {
            val ptx = PartialTransaction()
            CrowdFund().craftFund(ptx, registerTX.outRef(0), ALICE)
            Cash().craftSpend(ptx, 1000.DOLLARS, MINI_CORP_PUBKEY, aliceWallet)
            ptx.signWith(ALICE_KEY)
            val stx = ptx.toSignedTransaction()
            // this verify passes - the transaction contains an output cash, necessary to verify the fund command
            stx.verify().toLedgerTransaction(TEST_TX_TIME, TEST_KEYS_TO_CORP_MAP, SecureHash.randomSHA256())
        }

        // MiniCorp closes their campaign.
        val fundedTX: LedgerTransaction = run {
            val ptx = PartialTransaction()
            CrowdFund().craftFunded(ptx, pledgeTX.outRef(0))
            ptx.signWith(MINI_CORP_KEY)
            val stx = ptx.toSignedTransaction()
            stx.verify().toLedgerTransaction(TEST_TX_TIME + 8.days, TEST_KEYS_TO_CORP_MAP, SecureHash.randomSHA256())
        }

        // This verification passes
        TransactionGroup(setOf(registerTX, pledgeTX, fundedTX), setOf(aliceWalletTX)).verify(TEST_PROGRAM_MAP)

    }

    }