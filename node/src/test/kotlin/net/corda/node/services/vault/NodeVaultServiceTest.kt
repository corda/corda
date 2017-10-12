package net.corda.node.services.vault

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.services.*
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.QueryCriteria.*
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.toNonEmptySet
import net.corda.finance.*
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.asset.DUMMY_CASH_ISSUER
import net.corda.finance.contracts.asset.DUMMY_CASH_ISSUER_KEY
import net.corda.finance.contracts.getCashBalance
import net.corda.finance.schemas.CashSchemaV1
import net.corda.finance.utils.sumCash
import net.corda.node.utilities.CordaPersistence
import net.corda.testing.*
import net.corda.testing.contracts.fillWithSomeTestCash
import net.corda.testing.node.MockServices
import net.corda.testing.node.MockServices.Companion.makeTestDatabaseAndMockServices
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.After
import org.junit.Before
import org.junit.Test
import rx.observers.TestSubscriber
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NodeVaultServiceTest : TestDependencyInjectionBase() {
    companion object {
        private val cordappPackages = listOf("net.corda.finance.contracts.asset")
    }

    lateinit var services: MockServices
    private lateinit var issuerServices: MockServices
    val vaultService get() = services.vaultService as NodeVaultService
    lateinit var database: CordaPersistence

    @Before
    fun setUp() {
        LogHelper.setLevel(NodeVaultService::class)
        val databaseAndServices = makeTestDatabaseAndMockServices(keys = listOf(BOC_KEY, DUMMY_CASH_ISSUER_KEY),
                customSchemas = setOf(CashSchemaV1),
                cordappPackages = cordappPackages)
        database = databaseAndServices.first
        services = databaseAndServices.second
        issuerServices = MockServices(cordappPackages, DUMMY_CASH_ISSUER_KEY, BOC_KEY)
    }

    @After
    fun tearDown() {
        database.close()
        LogHelper.reset(NodeVaultService::class)
    }

    @Suspendable
    private fun VaultService.unconsumedCashStatesForSpending(amount: Amount<Currency>,
                                                             onlyFromIssuerParties: Set<AbstractParty>? = null,
                                                             notary: Party? = null,
                                                             lockId: UUID = UUID.randomUUID(),
                                                             withIssuerRefs: Set<OpaqueBytes>? = null): List<StateAndRef<Cash.State>> {

        val notaries = if (notary != null) listOf(notary) else null
        var baseCriteria: QueryCriteria = QueryCriteria.VaultQueryCriteria(notary = notaries)
        if (onlyFromIssuerParties != null || withIssuerRefs != null) {
            baseCriteria = baseCriteria.and(QueryCriteria.FungibleAssetQueryCriteria(
                    issuer = onlyFromIssuerParties?.toList(),
                    issuerRef = withIssuerRefs?.toList()))
        }

        return tryLockFungibleStatesForSpending(lockId, baseCriteria, amount, Cash.State::class.java)
    }


    @Test
    fun `states not local to instance`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, issuerServices, DUMMY_NOTARY, 3, 3, Random(0L))
        }
        database.transaction {
            val w1 = vaultService.queryBy<Cash.State>().states
            assertThat(w1).hasSize(3)

            val originalVault = vaultService
            val services2 = object : MockServices() {
                override val vaultService: NodeVaultService get() = originalVault
                override fun recordTransactions(notifyVault: Boolean, txs: Iterable<SignedTransaction>) {
                    for (stx in txs) {
                        validatedTransactions.addTransaction(stx)
                        vaultService.notify(stx.tx)
                    }
                }
            }

            val w2 = services2.vaultService.queryBy<Cash.State>().states
            assertThat(w2).hasSize(3)
        }
    }

    @Test
    fun `states for refs`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, issuerServices, DUMMY_NOTARY, 3, 3, Random(0L))
        }
        database.transaction {
            val w1 = vaultService.queryBy<Cash.State>().states
            assertThat(w1).hasSize(3)

            val states = vaultService.queryBy<Cash.State>(VaultQueryCriteria(stateRefs = listOf(w1[1].ref, w1[2].ref))).states
            assertThat(states).hasSize(2)
        }
    }

    @Test
    fun `states soft locking reserve and release`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, issuerServices, DUMMY_NOTARY, 3, 3, Random(0L))
        }
        database.transaction {

            val unconsumedStates = vaultService.queryBy<Cash.State>().states
            assertThat(unconsumedStates).hasSize(3)

            val stateRefsToSoftLock = NonEmptySet.of(unconsumedStates[1].ref, unconsumedStates[2].ref)

            // soft lock two of the three states
            val softLockId = UUID.randomUUID()
            vaultService.softLockReserve(softLockId, stateRefsToSoftLock)

            // all softlocked states
            val criteriaLocked = VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.LOCKED_ONLY))
            assertThat(vaultService.queryBy<Cash.State>(criteriaLocked).states).hasSize(2)
            // my softlocked states
            val criteriaByLockId = VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.SPECIFIED, listOf(softLockId)))
            assertThat(vaultService.queryBy<Cash.State>(criteriaByLockId).states).hasSize(2)

            // excluding softlocked states
            val unlockedStates1 = vaultService.queryBy<Cash.State>(VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.UNLOCKED_ONLY))).states
            assertThat(unlockedStates1).hasSize(1)

            // soft lock release one of the states explicitly
            vaultService.softLockRelease(softLockId, NonEmptySet.of(unconsumedStates[1].ref))
            val unlockedStates2 = vaultService.queryBy<Cash.State>(VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.UNLOCKED_ONLY))).states
            assertThat(unlockedStates2).hasSize(2)

            // soft lock release the rest by id
            vaultService.softLockRelease(softLockId)
            val unlockedStates = vaultService.queryBy<Cash.State>(VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.UNLOCKED_ONLY))).states
            assertThat(unlockedStates).hasSize(3)

            // should be back to original states
            assertThat(unlockedStates).isEqualTo(unconsumedStates)
        }
    }

    @Test
    fun `soft locking attempt concurrent reserve`() {
        val backgroundExecutor = Executors.newFixedThreadPool(2)
        val countDown = CountDownLatch(2)

        val softLockId1 = UUID.randomUUID()
        val softLockId2 = UUID.randomUUID()

        val criteriaByLockId1 = VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.SPECIFIED, listOf(softLockId1)))
        val criteriaByLockId2 = VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.SPECIFIED, listOf(softLockId2)))

        val vaultStates =
                database.transaction {
                    assertEquals(0.DOLLARS, services.getCashBalance(USD))
                    services.fillWithSomeTestCash(100.DOLLARS, issuerServices, DUMMY_NOTARY, 3, 3, Random(0L))
                }
        val stateRefsToSoftLock = (vaultStates.states.map { it.ref }).toNonEmptySet()
        println("State Refs:: $stateRefsToSoftLock")

        // 1st tx locks states
        backgroundExecutor.submit {
            try {
                database.transaction {
                    vaultService.softLockReserve(softLockId1, stateRefsToSoftLock)
                    assertThat(vaultService.queryBy<Cash.State>(criteriaByLockId1).states).hasSize(3)
                }
                println("SOFT LOCK STATES #1 succeeded")
            } catch (e: Throwable) {
                println("SOFT LOCK STATES #1 failed")
            } finally {
                countDown.countDown()
            }
        }

        // 2nd tx attempts to lock same states
        backgroundExecutor.submit {
            try {
                Thread.sleep(100)   // let 1st thread soft lock them 1st
                database.transaction {
                    vaultService.softLockReserve(softLockId2, stateRefsToSoftLock)
                    assertThat(vaultService.queryBy<Cash.State>(criteriaByLockId2).states).hasSize(3)
                }
                println("SOFT LOCK STATES #2 succeeded")
            } catch (e: Throwable) {
                println("SOFT LOCK STATES #2 failed")
            } finally {
                countDown.countDown()
            }
        }

        countDown.await()
        database.transaction {
            val lockStatesId1 = vaultService.queryBy<Cash.State>(criteriaByLockId1).states
            println("SOFT LOCK #1 final states: $lockStatesId1")
            assertThat(lockStatesId1.size).isIn(0, 3)
            val lockStatesId2 = vaultService.queryBy<Cash.State>(criteriaByLockId2).states
            println("SOFT LOCK #2 final states: $lockStatesId2")
            assertThat(lockStatesId2.size).isIn(0, 3)
        }
    }

    @Test
    fun `soft locking partial reserve states fails`() {
        val softLockId1 = UUID.randomUUID()
        val softLockId2 = UUID.randomUUID()

        val vaultStates =
                database.transaction {
                    assertEquals(0.DOLLARS, services.getCashBalance(USD))
                    services.fillWithSomeTestCash(100.DOLLARS, issuerServices, DUMMY_NOTARY, 3, 3, Random(0L))
                }
        val stateRefsToSoftLock = vaultStates.states.map { it.ref }
        println("State Refs:: $stateRefsToSoftLock")

        // lock 1st state with LockId1
        database.transaction {
            vaultService.softLockReserve(softLockId1, NonEmptySet.of(stateRefsToSoftLock.first()))
            val criteriaByLockId1 = VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.SPECIFIED, listOf(softLockId1)))
            assertThat(vaultService.queryBy<Cash.State>(criteriaByLockId1).states).hasSize(1)
        }

        // attempt to lock all 3 states with LockId2
        database.transaction {
            assertThatExceptionOfType(StatesNotAvailableException::class.java).isThrownBy(
                    { vaultService.softLockReserve(softLockId2, stateRefsToSoftLock.toNonEmptySet()) }
            ).withMessageContaining("only 2 rows available").withNoCause()
        }
    }

    @Test
    fun `attempt to lock states already soft locked by me`() {
        val softLockId1 = UUID.randomUUID()
        val criteriaByLockId1 = VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.SPECIFIED, listOf(softLockId1)))

        val vaultStates =
                database.transaction {
                    assertEquals(0.DOLLARS, services.getCashBalance(USD))
                    services.fillWithSomeTestCash(100.DOLLARS, issuerServices, DUMMY_NOTARY, 3, 3, Random(0L))
                }
        val stateRefsToSoftLock = (vaultStates.states.map { it.ref }).toNonEmptySet()
        println("State Refs:: $stateRefsToSoftLock")

        // lock states with LockId1
        database.transaction {
            vaultService.softLockReserve(softLockId1, stateRefsToSoftLock)
            assertThat(vaultService.queryBy<Cash.State>(criteriaByLockId1).states).hasSize(3)
        }

        // attempt to relock same states with LockId1
        database.transaction {
            vaultService.softLockReserve(softLockId1, stateRefsToSoftLock)
            assertThat(vaultService.queryBy<Cash.State>(criteriaByLockId1).states).hasSize(3)
        }
    }

    @Test
    fun `lock additional states to some already soft locked by me`() {

        val softLockId1 = UUID.randomUUID()
        val criteriaByLockId1 = VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.SPECIFIED, listOf(softLockId1)))

        val vaultStates =
                database.transaction {
                    assertEquals(0.DOLLARS, services.getCashBalance(USD))
                    services.fillWithSomeTestCash(100.DOLLARS, issuerServices, DUMMY_NOTARY, 3, 3, Random(0L))
                }
        val stateRefsToSoftLock = vaultStates.states.map { it.ref }
        println("State Refs:: $stateRefsToSoftLock")

        // lock states with LockId1
        database.transaction {
            vaultService.softLockReserve(softLockId1, NonEmptySet.of(stateRefsToSoftLock.first()))
            assertThat(vaultService.queryBy<Cash.State>(criteriaByLockId1).states).hasSize(1)
        }

        // attempt to lock all states with LockId1 (including previously already locked one)
        database.transaction {
            vaultService.softLockReserve(softLockId1, stateRefsToSoftLock.toNonEmptySet())
            assertThat(vaultService.queryBy<Cash.State>(criteriaByLockId1).states).hasSize(3)
        }
    }

    @Test
    fun `unconsumedStatesForSpending exact amount`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, issuerServices, DUMMY_NOTARY, 1, 1, Random(0L))
        }
        database.transaction {

            val unconsumedStates = vaultService.queryBy<Cash.State>().states
            assertThat(unconsumedStates).hasSize(1)

            val spendableStatesUSD = vaultService.unconsumedCashStatesForSpending(100.DOLLARS)
            spendableStatesUSD.forEach(::println)
            assertThat(spendableStatesUSD).hasSize(1)
            assertThat(spendableStatesUSD[0].state.data.amount.quantity).isEqualTo(100L * 100)
            val criteriaLocked = VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.LOCKED_ONLY))
            assertThat(vaultService.queryBy<Cash.State>(criteriaLocked).states).hasSize(1)
        }
    }

    @Test
    fun `unconsumedStatesForSpending from two issuer parties`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, issuerServices, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (DUMMY_CASH_ISSUER))
            services.fillWithSomeTestCash(100.DOLLARS, issuerServices, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (BOC.ref(1)))
        }
        database.transaction {
            val spendableStatesUSD = vaultService.unconsumedCashStatesForSpending(200.DOLLARS,
                    onlyFromIssuerParties = setOf(DUMMY_CASH_ISSUER.party, BOC))
            spendableStatesUSD.forEach(::println)
            assertThat(spendableStatesUSD).hasSize(2)
            assertThat(spendableStatesUSD[0].state.data.amount.token.issuer).isIn(DUMMY_CASH_ISSUER, BOC.ref(1))
            assertThat(spendableStatesUSD[1].state.data.amount.token.issuer).isIn(DUMMY_CASH_ISSUER, BOC.ref(1))
            assertThat(spendableStatesUSD[0].state.data.amount.token.issuer).isNotEqualTo(spendableStatesUSD[1].state.data.amount.token.issuer)
        }
    }

    @Test
    fun `unconsumedStatesForSpending from specific issuer party and refs`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, issuerServices, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (DUMMY_CASH_ISSUER))
            services.fillWithSomeTestCash(100.DOLLARS, issuerServices, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (BOC.ref(1)), ref = OpaqueBytes.of(1))
            services.fillWithSomeTestCash(100.DOLLARS, issuerServices, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (BOC.ref(2)), ref = OpaqueBytes.of(2))
            services.fillWithSomeTestCash(100.DOLLARS, issuerServices, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (BOC.ref(3)), ref = OpaqueBytes.of(3))
        }
        database.transaction {
            val unconsumedStates = vaultService.queryBy<Cash.State>().states
            assertThat(unconsumedStates).hasSize(4)

            val spendableStatesUSD = vaultService.unconsumedCashStatesForSpending(200.DOLLARS,
                    onlyFromIssuerParties = setOf(BOC), withIssuerRefs = setOf(OpaqueBytes.of(1), OpaqueBytes.of(2)))
            assertThat(spendableStatesUSD).hasSize(2)
            assertThat(spendableStatesUSD[0].state.data.amount.token.issuer.party).isEqualTo(BOC)
            assertThat(spendableStatesUSD[0].state.data.amount.token.issuer.reference).isIn(BOC.ref(1).reference, BOC.ref(2).reference)
            assertThat(spendableStatesUSD[1].state.data.amount.token.issuer.reference).isIn(BOC.ref(1).reference, BOC.ref(2).reference)
            assertThat(spendableStatesUSD[0].state.data.amount.token.issuer.reference).isNotEqualTo(spendableStatesUSD[1].state.data.amount.token.issuer.reference)
        }
    }

    @Test
    fun `unconsumedStatesForSpending insufficient amount`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, issuerServices, DUMMY_NOTARY, 1, 1, Random(0L))
        }
        database.transaction {
            val unconsumedStates = vaultService.queryBy<Cash.State>().states
            assertThat(unconsumedStates).hasSize(1)

            val spendableStatesUSD = vaultService.unconsumedCashStatesForSpending(110.DOLLARS)
            spendableStatesUSD.forEach(::println)
            assertThat(spendableStatesUSD).hasSize(0)
            val criteriaLocked = VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.LOCKED_ONLY))
            assertThat(vaultService.queryBy<Cash.State>(criteriaLocked).states).hasSize(0)
        }
    }

    @Test
    fun `unconsumedStatesForSpending small amount`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, issuerServices, DUMMY_NOTARY, 2, 2, Random(0L))
        }
        database.transaction {
            val unconsumedStates = vaultService.queryBy<Cash.State>().states
            assertThat(unconsumedStates).hasSize(2)

            val spendableStatesUSD = vaultService.unconsumedCashStatesForSpending(1.DOLLARS)
            spendableStatesUSD.forEach(::println)
            assertThat(spendableStatesUSD).hasSize(1)
            assertThat(spendableStatesUSD[0].state.data.amount.quantity).isGreaterThanOrEqualTo(100L)
            val criteriaLocked = VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.LOCKED_ONLY))
            assertThat(vaultService.queryBy<Cash.State>(criteriaLocked).states).hasSize(1)
        }
    }

    @Test
    fun `states soft locking query granularity`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, issuerServices, DUMMY_NOTARY, 10, 10, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, issuerServices, DUMMY_NOTARY, 10, 10, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, issuerServices, DUMMY_NOTARY, 10, 10, Random(0L))
        }
        database.transaction {
            var unlockedStates = 30
            val allStates = vaultService.queryBy<Cash.State>().states
            assertThat(allStates).hasSize(unlockedStates)

            var lockedCount = 0
            for (i in 1..5) {
                val lockId = UUID.randomUUID()
                val spendableStatesUSD = vaultService.unconsumedCashStatesForSpending(20.DOLLARS, lockId = lockId)
                spendableStatesUSD.forEach(::println)
                assertThat(spendableStatesUSD.size <= unlockedStates)
                unlockedStates -= spendableStatesUSD.size
                val criteriaLocked = VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.SPECIFIED, listOf(lockId)))
                val lockedStates = vaultService.queryBy<Cash.State>(criteriaLocked).states
                if (spendableStatesUSD.isNotEmpty()) {
                    assertEquals(spendableStatesUSD.size, lockedStates.size)
                    val lockedTotal = lockedStates.map { it.state.data }.sumCash()
                    val foundAmount = spendableStatesUSD.map { it.state.data }.sumCash()
                    assertThat(foundAmount.toDecimal() >= BigDecimal("20.00"))
                    assertThat(lockedTotal == foundAmount)
                    lockedCount += lockedStates.size
                }
            }
            val criteriaLocked = VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.LOCKED_ONLY))
            assertThat(vaultService.queryBy<Cash.State>(criteriaLocked).states).hasSize(lockedCount)
        }
    }

    @Test
    fun addNoteToTransaction() {
        val megaCorpServices = MockServices(cordappPackages, MEGA_CORP_KEY)
        database.transaction {
            val freshKey = services.myInfo.chooseIdentity().owningKey

            // Issue a txn to Send us some Money
            val usefulBuilder = TransactionBuilder(null).apply {
                Cash().generateIssue(this, 100.DOLLARS `issued by` MEGA_CORP.ref(1), AnonymousParty(freshKey), DUMMY_NOTARY)
            }
            val usefulTX = megaCorpServices.signInitialTransaction(usefulBuilder)

            services.recordTransactions(usefulTX)

            vaultService.addNoteToTransaction(usefulTX.id, "USD Sample Note 1")
            vaultService.addNoteToTransaction(usefulTX.id, "USD Sample Note 2")
            vaultService.addNoteToTransaction(usefulTX.id, "USD Sample Note 3")
            assertEquals(3, vaultService.getTransactionNotes(usefulTX.id).count())

            // Issue more Money (GBP)
            val anotherBuilder = TransactionBuilder(null).apply {
                Cash().generateIssue(this, 200.POUNDS `issued by` MEGA_CORP.ref(1), AnonymousParty(freshKey), DUMMY_NOTARY)
            }
            val anotherTX = megaCorpServices.signInitialTransaction(anotherBuilder)

            services.recordTransactions(anotherTX)

            vaultService.addNoteToTransaction(anotherTX.id, "GBP Sample Note 1")
            assertEquals(1, vaultService.getTransactionNotes(anotherTX.id).count())
        }
    }

    @Test
    fun `is ownable state relevant`() {
        val service = vaultService
        val amount = Amount(1000, Issued(BOC.ref(1), GBP))
        val wellKnownCash = Cash.State(amount, services.myInfo.chooseIdentity())
        val myKeys = services.keyManagementService.filterMyKeys(listOf(wellKnownCash.owner.owningKey))
        assertTrue { service.isRelevant(wellKnownCash, myKeys.toSet()) }

        val anonymousIdentity = services.keyManagementService.freshKeyAndCert(services.myInfo.chooseIdentityAndCert(), false)
        val anonymousCash = Cash.State(amount, anonymousIdentity.party)
        val anonymousKeys = services.keyManagementService.filterMyKeys(listOf(anonymousCash.owner.owningKey))
        assertTrue { service.isRelevant(anonymousCash, anonymousKeys.toSet()) }

        val thirdPartyIdentity = AnonymousParty(generateKeyPair().public)
        val thirdPartyCash = Cash.State(amount, thirdPartyIdentity)
        val thirdPartyKeys = services.keyManagementService.filterMyKeys(listOf(thirdPartyCash.owner.owningKey))
        assertFalse { service.isRelevant(thirdPartyCash, thirdPartyKeys.toSet()) }
    }

    // TODO: Unit test linear state relevancy checks

    @Test
    fun `correct updates are generated for general transactions`() {
        val service = vaultService
        val vaultSubscriber = TestSubscriber<Vault.Update<*>>().apply {
            service.updates.subscribe(this)
        }

        val anonymousIdentity = services.keyManagementService.freshKeyAndCert(services.myInfo.chooseIdentityAndCert(), false)
        val thirdPartyIdentity = AnonymousParty(generateKeyPair().public)
        val amount = Amount(1000, Issued(BOC.ref(1), GBP))

        // Issue then move some cash
        val issueTx = TransactionBuilder(services.myInfo.chooseIdentity()).apply {
            Cash().generateIssue(this,
                    amount, anonymousIdentity.party, services.myInfo.chooseIdentity())
        }.toWireTransaction(services)
        val cashState = StateAndRef(issueTx.outputs.single(), StateRef(issueTx.id, 0))

        database.transaction { service.notify(issueTx) }
        val expectedIssueUpdate = Vault.Update(emptySet(), setOf(cashState), null)

        database.transaction {
            val moveTx = TransactionBuilder(services.myInfo.chooseIdentity()).apply {
                Cash.generateSpend(services, this, Amount(1000, GBP), thirdPartyIdentity)
            }.toWireTransaction(services)
            service.notify(moveTx)
        }
        val expectedMoveUpdate = Vault.Update(setOf(cashState), emptySet(), null)

        val observedUpdates = vaultSubscriber.onNextEvents
        assertEquals(observedUpdates, listOf(expectedIssueUpdate, expectedMoveUpdate))
    }

    @Test
    fun `correct updates are generated when changing notaries`() {
        val service = vaultService
        val notary = services.myInfo.chooseIdentity()

        val vaultSubscriber = TestSubscriber<Vault.Update<*>>().apply {
            service.updates.subscribe(this)
        }

        val anonymousIdentity = services.keyManagementService.freshKeyAndCert(services.myInfo.chooseIdentityAndCert(), false)
        val thirdPartyIdentity = AnonymousParty(generateKeyPair().public)
        val amount = Amount(1000, Issued(BOC.ref(1), GBP))

        // Issue some cash
        val issueTxBuilder = TransactionBuilder(notary).apply {
            Cash().generateIssue(this, amount, anonymousIdentity.party, notary)
        }
        val issueStx = services.signInitialTransaction(issueTxBuilder)
        // We need to record the issue transaction so inputs can be resolved for the notary change transaction
        services.validatedTransactions.addTransaction(issueStx)

        val initialCashState = StateAndRef(issueStx.tx.outputs.single(), StateRef(issueStx.id, 0))

        // Change notary
        val newNotary = DUMMY_NOTARY
        val changeNotaryTx = NotaryChangeWireTransaction(listOf(initialCashState.ref), issueStx.notary!!, newNotary)
        val cashStateWithNewNotary = StateAndRef(initialCashState.state.copy(notary = newNotary), StateRef(changeNotaryTx.id, 0))

        database.transaction {
            service.notifyAll(listOf(issueStx.tx, changeNotaryTx))
        }

        // Move cash
        val moveTx = database.transaction {
            TransactionBuilder(newNotary).apply {
                Cash.generateSpend(services, this, Amount(1000, GBP), thirdPartyIdentity)
            }.toWireTransaction(services)
        }

        database.transaction {
            service.notify(moveTx)
        }

        val expectedIssueUpdate = Vault.Update(emptySet(), setOf(initialCashState), null)
        val expectedNotaryChangeUpdate = Vault.Update(setOf(initialCashState), setOf(cashStateWithNewNotary), null, Vault.UpdateType.NOTARY_CHANGE)
        val expectedMoveUpdate = Vault.Update(setOf(cashStateWithNewNotary), emptySet(), null)

        val observedUpdates = vaultSubscriber.onNextEvents
        assertEquals(observedUpdates, listOf(expectedIssueUpdate, expectedNotaryChangeUpdate, expectedMoveUpdate))
    }
}
