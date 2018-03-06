/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.enterprise.perftestcordapp.contracts

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import com.r3.corda.enterprise.perftestcordapp.DOLLARS
import com.r3.corda.enterprise.perftestcordapp.`issued by`
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.CASH
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.Cash
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.DUMMY_CASH_ISSUER_KEY
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.STATE
import net.corda.core.contracts.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.days
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.testing.core.*
import net.corda.testing.dsl.EnforceVerifyOrFail
import net.corda.testing.dsl.TransactionDSL
import net.corda.testing.dsl.TransactionDSLInterpreter
import net.corda.testing.internal.TEST_TX_TIME
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import net.corda.testing.node.transaction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*

// TODO: The generate functions aren't tested by these tests: add them.

interface CommercialPaperTestTemplate {
    fun getPaper(): CommercialPaper.State
    fun getIssueCommand(notary: Party): CommandData
    fun getRedeemCommand(notary: Party): CommandData
    fun getMoveCommand(): CommandData
    fun getContract(): ContractClassName
}

private val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
private val MEGA_CORP get() = megaCorp.party
private val MEGA_CORP_PUBKEY get() = megaCorp.keyPair.public


class KotlinCommercialPaperTest : CommercialPaperTestTemplate {
    override fun getPaper(): CommercialPaper.State = CommercialPaper.State(
            issuance = MEGA_CORP.ref(123),
            owner = MEGA_CORP,
            faceValue = 1000.DOLLARS `issued by` MEGA_CORP.ref(123),
            maturityDate = TEST_TX_TIME + 7.days
    )

    override fun getIssueCommand(notary: Party): CommandData = CommercialPaper.Commands.Issue()
    override fun getRedeemCommand(notary: Party): CommandData = CommercialPaper.Commands.Redeem()
    override fun getMoveCommand(): CommandData = CommercialPaper.Commands.Move()
    override fun getContract() = CommercialPaper.CP_PROGRAM_ID
}

class KotlinCommercialPaperLegacyTest : CommercialPaperTestTemplate {
    override fun getPaper(): CommercialPaper.State = CommercialPaper.State(
            issuance = MEGA_CORP.ref(123),
            owner = MEGA_CORP,
            faceValue = 1000.DOLLARS `issued by` MEGA_CORP.ref(123),
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
        @Parameterized.Parameters @JvmStatic
        fun data() = listOf(KotlinCommercialPaperTest(), KotlinCommercialPaperLegacyTest())

        private val alice = TestIdentity(ALICE_NAME, 70)
        private val dummyNotary = TestIdentity(DUMMY_NOTARY_NAME, 20)
        private val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "London", "GB"))
        private val ALICE get() = alice.party
        private val ALICE_PUBKEY get() = alice.keyPair.public
        private val DUMMY_NOTARY get() = dummyNotary.party
        private val MINI_CORP get() = miniCorp.party
        private val MINI_CORP_PUBKEY get() = miniCorp.keyPair.public

    }

    @Parameterized.Parameter
    lateinit var thisTest: CommercialPaperTestTemplate
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    val issuer = MEGA_CORP.ref(123)
    private val ledgerServices = MockServices(emptyList(), MEGA_CORP.name, rigorousMock<IdentityServiceInternal>().also {
        doReturn(MEGA_CORP).whenever(it).partyFromKey(MEGA_CORP_PUBKEY)
        doReturn(MINI_CORP).whenever(it).partyFromKey(MINI_CORP_PUBKEY)
        doReturn(null).whenever(it).partyFromKey(ALICE_PUBKEY)
    })


    @Test
    fun `trade lifecycle test`() {
        val someProfits = 1200.DOLLARS `issued by` issuer
        ledgerServices.ledger(DUMMY_NOTARY) {
            unverifiedTransaction {
                attachment(Cash.PROGRAM_ID)
                output(Cash.PROGRAM_ID, "alice's $900", 900.DOLLARS.CASH issuedBy issuer ownedBy ALICE)
                output(Cash.PROGRAM_ID, "some profits", someProfits.STATE ownedBy MEGA_CORP)
            }

            // Some CP is issued onto the ledger by MegaCorp.
            transaction("Issuance") {
                attachments(CP_PROGRAM_ID, CommercialPaper.CP_PROGRAM_ID)
                output(thisTest.getContract(), "paper", thisTest.getPaper())
                command(MEGA_CORP_PUBKEY, thisTest.getIssueCommand(DUMMY_NOTARY))
                timeWindow(TEST_TX_TIME)
                this.verifies()
            }

            // The CP is sold to alice for her $900, $100 less than the face value. At 10% interest after only 7 days,
            // that sounds a bit too good to be true!
            transaction("Trade") {
                attachments(Cash.PROGRAM_ID, CommercialPaper.CP_PROGRAM_ID)
                input("paper")
                input("alice's $900")
                output(Cash.PROGRAM_ID, "borrowed $900", 900.DOLLARS.CASH issuedBy issuer ownedBy MEGA_CORP)
                output(thisTest.getContract(), "alice's paper", "paper".output<CommercialPaper.State>().withOwner(ALICE))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                command(MEGA_CORP_PUBKEY, thisTest.getMoveCommand())
                this.verifies()
            }

            // Time passes, and Alice redeem's her CP for $1000, netting a $100 profit. MegaCorp has received $1200
            // as a single payment from somewhere and uses it to pay Alice off, keeping the remaining $200 as change.
            transaction("Redemption") {
                attachments(CP_PROGRAM_ID, CommercialPaper.CP_PROGRAM_ID)
                input("alice's paper")
                input("some profits")

                fun TransactionDSL<TransactionDSLInterpreter>.outputs(aliceGetsBack: Amount<Issued<Currency>>) {
                    output(Cash.PROGRAM_ID, "Alice's profit", aliceGetsBack.STATE ownedBy ALICE)
                    output(Cash.PROGRAM_ID, "Change", (someProfits - aliceGetsBack).STATE ownedBy MEGA_CORP)
                }

                command(MEGA_CORP_PUBKEY, Cash.Commands.Move())
                command(ALICE_PUBKEY, thisTest.getRedeemCommand(DUMMY_NOTARY))

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
                    output(thisTest.getContract(), "paper".output<CommercialPaper.State>())
                    this `fails with` "must be destroyed"
                }

                this.verifies()
            }
        }
    }

    private fun transaction(script: TransactionDSL<TransactionDSLInterpreter>.() -> EnforceVerifyOrFail) = run {
        ledgerServices.transaction(DUMMY_NOTARY, script)
    }

    @Test
    fun `key mismatch at issue`() {
        transaction {
            attachment(CP_PROGRAM_ID)
            attachment(CP_PROGRAM_ID)
            output(thisTest.getContract(), thisTest.getPaper())
            command(MINI_CORP_PUBKEY, thisTest.getIssueCommand(DUMMY_NOTARY))
            timeWindow(TEST_TX_TIME)
            this `fails with` "output states are issued by a command signer"
        }
    }

    @Test
    fun `face value is not zero`() {
        transaction {
            attachment(CP_PROGRAM_ID)
            attachment(CP_PROGRAM_ID)
            output(thisTest.getContract(), thisTest.getPaper().withFaceValue(0.DOLLARS `issued by` issuer))
            command(MEGA_CORP_PUBKEY, thisTest.getIssueCommand(DUMMY_NOTARY))
            timeWindow(TEST_TX_TIME)
            this `fails with` "output values sum to more than the inputs"
        }
    }

    @Test
    fun `maturity date not in the past`() {
        transaction {
            attachment(CP_PROGRAM_ID)
            attachment(CP_PROGRAM_ID)
            output(thisTest.getContract(), thisTest.getPaper().withMaturityDate(TEST_TX_TIME - 10.days))
            command(MEGA_CORP_PUBKEY, thisTest.getIssueCommand(DUMMY_NOTARY))
            timeWindow(TEST_TX_TIME)
            this `fails with` "maturity date is not in the past"
        }
    }

    @Test
    fun `issue cannot replace an existing state`() {
        transaction {
            attachment(CP_PROGRAM_ID)
            attachment(CP_PROGRAM_ID)
            input(thisTest.getContract(), thisTest.getPaper())
            output(thisTest.getContract(), thisTest.getPaper())
            command(MEGA_CORP_PUBKEY, thisTest.getIssueCommand(DUMMY_NOTARY))
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

    private val notaryServices = MockServices(emptyList(), MEGA_CORP.name, rigorousMock(), dummyNotary.keyPair)
    private val issuerServices = MockServices(emptyList(), MEGA_CORP.name, rigorousMock(), DUMMY_CASH_ISSUER_KEY)

    private lateinit var moveTX: SignedTransaction
}
