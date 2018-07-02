package net.corda.node.services.vault

import co.paralleluniverse.fibers.Suspendable
import com.nhaarman.mockito_kotlin.argThat
import com.nhaarman.mockito_kotlin.doNothing
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.*
import net.corda.core.crypto.NullKeys
import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.*
import net.corda.core.internal.NotaryChangeTransactionBuilder
import net.corda.core.internal.packageName
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.StatesNotAvailableException
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.QueryCriteria.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.toNonEmptySet
import net.corda.finance.*
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.getCashBalance
import net.corda.finance.schemas.CashSchemaV1
import net.corda.finance.utils.sumCash
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.node.services.api.WritableTransactionStorage
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.core.*
import net.corda.testing.internal.LogHelper
import net.corda.testing.internal.rigorousMock
import net.corda.testing.internal.vault.VaultFiller
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestIdentityService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import rx.observers.TestSubscriber
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NodeVaultServiceTest {
    private companion object {
        val cordappPackages = listOf("net.corda.finance.contracts.asset", CashSchemaV1::class.packageName)
        val dummyCashIssuer = TestIdentity(CordaX500Name("Snake Oil Issuer", "London", "GB"), 10)
        val DUMMY_CASH_ISSUER = dummyCashIssuer.ref(1)
        val bankOfCorda = TestIdentity(BOC_NAME)
        val dummyNotary = TestIdentity(DUMMY_NOTARY_NAME, 20)
        val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
        val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "London", "GB"))
        val BOC get() = bankOfCorda.party
        val BOC_IDENTITY get() = bankOfCorda.identity
        val DUMMY_CASH_ISSUER_IDENTITY get() = dummyCashIssuer.identity
        val DUMMY_NOTARY get() = dummyNotary.party
        val DUMMY_NOTARY_IDENTITY get() = dummyNotary.identity
        val MEGA_CORP get() = megaCorp.party
        val MEGA_CORP_KEY get() = megaCorp.keyPair
        val MEGA_CORP_PUBKEY get() = megaCorp.publicKey
        val MEGA_CORP_IDENTITY get() = megaCorp.identity
        val MINI_CORP get() = miniCorp.party
        val MINI_CORP_IDENTITY get() = miniCorp.identity
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    private lateinit var services: MockServices
    private lateinit var vaultFiller: VaultFiller
    private lateinit var identity: PartyAndCertificate
    private lateinit var issuerServices: MockServices
    private lateinit var bocServices: MockServices
    private val vaultService get() = services.vaultService as NodeVaultService
    private lateinit var database: CordaPersistence

    @Before
    fun setUp() {
        LogHelper.setLevel(NodeVaultService::class)
        val databaseAndServices = MockServices.makeTestDatabaseAndMockServices(
                cordappPackages,
                makeTestIdentityService(MEGA_CORP_IDENTITY, MINI_CORP_IDENTITY, DUMMY_CASH_ISSUER_IDENTITY, DUMMY_NOTARY_IDENTITY),
                megaCorp)
        database = databaseAndServices.first
        services = databaseAndServices.second
        vaultFiller = VaultFiller(services, dummyNotary)
        // This is safe because MockServices only ever have a single identity
        identity = services.myInfo.singleIdentityAndCert()
        issuerServices = MockServices(cordappPackages, dummyCashIssuer, rigorousMock())
        bocServices = MockServices(cordappPackages, bankOfCorda, rigorousMock())
        services.identityService.verifyAndRegisterIdentity(DUMMY_CASH_ISSUER_IDENTITY)
        services.identityService.verifyAndRegisterIdentity(BOC_IDENTITY)
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
    fun `duplicate insert of transaction does not fail`() {
        database.transaction {
            val cash = Cash()
            val howMuch = 100.DOLLARS
            val issuance = TransactionBuilder(null as Party?)
            cash.generateIssue(issuance, Amount(howMuch.quantity, Issued(DUMMY_CASH_ISSUER, howMuch.token)), services.myInfo.singleIdentity(), dummyNotary.party)
            val transaction = issuerServices.signInitialTransaction(issuance, DUMMY_CASH_ISSUER.party.owningKey)
            services.recordTransactions(transaction)
            services.recordTransactions(transaction)
        }
    }

    @Test
    fun `states not local to instance`() {
        database.transaction {
            vaultFiller.fillWithSomeTestCash(100.DOLLARS, issuerServices, 3, DUMMY_CASH_ISSUER)
        }
        database.transaction {
            val w1 = vaultService.queryBy<Cash.State>().states
            assertThat(w1).hasSize(3)

            val originalVault = vaultService
            val services2 = object : MockServices(emptyList(), MEGA_CORP.name, rigorousMock()) {
                override val vaultService: NodeVaultService get() = originalVault
                override fun recordTransactions(statesToRecord: StatesToRecord, txs: Iterable<SignedTransaction>) {
                    for (stx in txs) {
                        (validatedTransactions as WritableTransactionStorage).addTransaction(stx)
                        vaultService.notify(statesToRecord, stx.tx)
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
            vaultFiller.fillWithSomeTestCash(100.DOLLARS, issuerServices, 3, DUMMY_CASH_ISSUER)
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
            vaultFiller.fillWithSomeTestCash(100.DOLLARS, issuerServices, 3, DUMMY_CASH_ISSUER)
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
                    vaultFiller.fillWithSomeTestCash(100.DOLLARS, issuerServices, 3, DUMMY_CASH_ISSUER)
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
                    vaultFiller.fillWithSomeTestCash(100.DOLLARS, issuerServices, 3, DUMMY_CASH_ISSUER)
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
                    vaultFiller.fillWithSomeTestCash(100.DOLLARS, issuerServices, 3, DUMMY_CASH_ISSUER)
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
                    vaultFiller.fillWithSomeTestCash(100.DOLLARS, issuerServices, 3, DUMMY_CASH_ISSUER)
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
            vaultFiller.fillWithSomeTestCash(100.DOLLARS, issuerServices, 1, DUMMY_CASH_ISSUER)
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
            vaultFiller.fillWithSomeTestCash(100.DOLLARS, issuerServices, 1, DUMMY_CASH_ISSUER)
            vaultFiller.fillWithSomeTestCash(100.DOLLARS, bocServices, 1, BOC.ref(1))
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
            vaultFiller.fillWithSomeTestCash(100.DOLLARS, issuerServices, 1, DUMMY_CASH_ISSUER)
            vaultFiller.fillWithSomeTestCash(100.DOLLARS, bocServices, 1, BOC.ref(1))
            vaultFiller.fillWithSomeTestCash(100.DOLLARS, bocServices, 1, BOC.ref(2))
            vaultFiller.fillWithSomeTestCash(100.DOLLARS, bocServices, 1, BOC.ref(3))
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
            vaultFiller.fillWithSomeTestCash(100.DOLLARS, issuerServices, 1, DUMMY_CASH_ISSUER)
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
            vaultFiller.fillWithSomeTestCash(100.DOLLARS, issuerServices, 2, DUMMY_CASH_ISSUER)
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
            listOf(USD, GBP, CHF).forEach {
                vaultFiller.fillWithSomeTestCash(AMOUNT(100, it), issuerServices, 10, DUMMY_CASH_ISSUER)
            }
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
        val megaCorpServices = MockServices(cordappPackages, MEGA_CORP.name, rigorousMock(), MEGA_CORP_KEY)
        database.transaction {
            val freshKey = identity.owningKey

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
        val wellKnownCash = Cash.State(amount, identity.party)
        val myKeys = services.keyManagementService.filterMyKeys(listOf(wellKnownCash.owner.owningKey))
        assertTrue { service.isRelevant(wellKnownCash, myKeys.toSet()) }

        val anonymousIdentity = services.keyManagementService.freshKeyAndCert(identity, false)
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
        val notary = identity.party
        val vaultSubscriber = TestSubscriber<Vault.Update<*>>().apply {
            vaultService.updates.subscribe(this)
        }

        val identity = services.myInfo.singleIdentityAndCert()
        val anonymousIdentity = services.keyManagementService.freshKeyAndCert(identity, false)
        // We use a random key pair to pay to here, as we don't actually use the cash once sent
        val thirdPartyIdentity = AnonymousParty(generateKeyPair().public)
        val amount = Amount(1000, Issued(BOC.ref(1), GBP))

        // Issue then move some cash
        val issueBuilder = TransactionBuilder(notary).apply {
            Cash().generateIssue(this, amount, anonymousIdentity.party.anonymise(), identity.party)
        }
        val issueTx = issueBuilder.toWireTransactionNew(bocServices)
        val cashState = StateAndRef(issueTx.outputs.single(), StateRef(issueTx.id, 0))

        // ensure transaction contract state is persisted in DBStorage
        val signedIssuedTx = services.signInitialTransaction(issueBuilder)
        (services.validatedTransactions as WritableTransactionStorage).addTransaction(signedIssuedTx)

        database.transaction { vaultService.notify(StatesToRecord.ONLY_RELEVANT, issueTx) }
        val expectedIssueUpdate = Vault.Update(emptySet(), setOf(cashState), null)

        database.transaction {
            val moveBuilder = TransactionBuilder(notary).apply {
                Cash.generateSpend(services, this, Amount(1000, GBP), identity, thirdPartyIdentity)
            }
            val moveTx = moveBuilder.toWireTransactionNew(services)
            vaultService.notify(StatesToRecord.ONLY_RELEVANT, moveTx)
        }
        val expectedMoveUpdate = Vault.Update(setOf(cashState), emptySet(), null)

        // ensure transaction contract state is persisted in DBStorage
        val signedMoveTx = services.signInitialTransaction(issueBuilder)
        (services.validatedTransactions as WritableTransactionStorage).addTransaction(signedMoveTx)

        val observedUpdates = vaultSubscriber.onNextEvents
        assertEquals(observedUpdates, listOf(expectedIssueUpdate, expectedMoveUpdate))
    }

    @Test
    fun `correct updates are generated when changing notaries`() {
        val service = vaultService
        val notary = identity.party

        val vaultSubscriber = TestSubscriber<Vault.Update<*>>().apply {
            service.updates.subscribe(this)
        }

        val identity = services.myInfo.singleIdentityAndCert()
        assertEquals(services.identityService.partyFromKey(identity.owningKey), identity.party)
        val anonymousIdentity = services.keyManagementService.freshKeyAndCert(identity, false)
        val thirdPartyServices = MockServices(emptyList(), MEGA_CORP.name, rigorousMock<IdentityServiceInternal>().also {
            doNothing().whenever(it).justVerifyAndRegisterIdentity(argThat { name == MEGA_CORP.name })
        })
        val thirdPartyIdentity = thirdPartyServices.keyManagementService.freshKeyAndCert(thirdPartyServices.myInfo.singleIdentityAndCert(), false)
        val amount = Amount(1000, Issued(BOC.ref(1), GBP))

        // Issue some cash
        val issueTxBuilder = TransactionBuilder(notary).apply {
            Cash().generateIssue(this, amount, anonymousIdentity.party, notary)
        }
        val issueStx = bocServices.signInitialTransaction(issueTxBuilder)
        // We need to record the issue transaction so inputs can be resolved for the notary change transaction
        (services.validatedTransactions as WritableTransactionStorage).addTransaction(issueStx)

        val initialCashState = StateAndRef(issueStx.tx.outputs.single(), StateRef(issueStx.id, 0))

        // Change notary
        services.identityService.verifyAndRegisterIdentity(DUMMY_NOTARY_IDENTITY)
        val newNotary = DUMMY_NOTARY
        val changeNotaryTx = NotaryChangeTransactionBuilder(listOf(initialCashState.ref), issueStx.notary!!, newNotary).build()
        val cashStateWithNewNotary = StateAndRef(initialCashState.state.copy(notary = newNotary), StateRef(changeNotaryTx.id, 0))

        database.transaction {
            service.notifyAll(StatesToRecord.ONLY_RELEVANT, listOf(issueStx.tx, changeNotaryTx))
        }

        // ensure transaction contract state is persisted in DBStorage
        (services.validatedTransactions as WritableTransactionStorage).addTransaction(SignedTransaction(changeNotaryTx, listOf(NullKeys.NULL_SIGNATURE)))

        // Move cash
        val moveTxBuilder = database.transaction {
            TransactionBuilder(newNotary).apply {
                Cash.generateSpend(services, this, Amount(amount.quantity, GBP), identity, thirdPartyIdentity.party.anonymise())
            }
        }
        val moveTx = moveTxBuilder.toWireTransactionNew(services)

        // ensure transaction contract state is persisted in DBStorage
        val signedMoveTx = services.signInitialTransaction(moveTxBuilder)
        (services.validatedTransactions as WritableTransactionStorage).addTransaction(signedMoveTx)

        database.transaction {
            service.notify(StatesToRecord.ONLY_RELEVANT, moveTx)
        }

        val expectedIssueUpdate = Vault.Update(emptySet(), setOf(initialCashState), null)
        val expectedNotaryChangeUpdate = Vault.Update(setOf(initialCashState), setOf(cashStateWithNewNotary), null, Vault.UpdateType.NOTARY_CHANGE)
        val expectedMoveUpdate = Vault.Update(setOf(cashStateWithNewNotary), emptySet(), null)

        val observedUpdates = vaultSubscriber.onNextEvents
        assertEquals(observedUpdates, listOf(expectedIssueUpdate, expectedNotaryChangeUpdate, expectedMoveUpdate))
    }

    @Test
    fun observerMode() {
        fun countCash(): Long {
            return database.transaction {
                vaultService.queryBy(Cash.State::class.java, QueryCriteria.VaultQueryCriteria(), PageSpecification(1)).totalStatesAvailable
            }
        }
        val currentCashStates = countCash()

        // Send some minimalist dummy transaction.
        val txb = TransactionBuilder(DUMMY_NOTARY)
        txb.addOutputState(Cash.State(MEGA_CORP.ref(0), 100.DOLLARS, MINI_CORP), Cash::class.java.name)
        txb.addCommand(Cash.Commands.Move(), MEGA_CORP_PUBKEY)
        val wtx = txb.toWireTransactionNew(services)
        database.transaction {
            vaultService.notify(StatesToRecord.ONLY_RELEVANT, wtx)
        }

        // ensure transaction contract state is persisted in DBStorage
        val signedTxb = services.signInitialTransaction(txb)
        (services.validatedTransactions as WritableTransactionStorage).addTransaction(signedTxb)

        // Check that it was ignored as irrelevant.
        assertEquals(currentCashStates, countCash())

        // Now try again and check it was accepted.
        database.transaction {
            vaultService.notify(StatesToRecord.ALL_VISIBLE, wtx)
        }
        assertEquals(currentCashStates + 1, countCash())
    }

    @Test
    fun `insert equal cash states issued by single transaction`() {
        val nodeIdentity = MEGA_CORP
        val coins = listOf(1.DOLLARS, 1.DOLLARS).map { it.issuedBy(nodeIdentity.ref(1)) }

        //create single transaction with 2 'identical' cash outputs
        val txb = TransactionBuilder(DUMMY_NOTARY)
        coins.map { txb.addOutputState(TransactionState(Cash.State(it, nodeIdentity), Cash.PROGRAM_ID, DUMMY_NOTARY)) }
        txb.addCommand(Cash.Commands.Issue(), nodeIdentity.owningKey)
        val issueTx = txb.toWireTransactionNew(services)

        // ensure transaction contract state is persisted in DBStorage
        val signedIssuedTx = services.signInitialTransaction(txb)
        (services.validatedTransactions as WritableTransactionStorage).addTransaction(signedIssuedTx)

        database.transaction { vaultService.notify(StatesToRecord.ONLY_RELEVANT, issueTx) }

        val recordedStates = database.transaction {
            vaultService.queryBy<Cash.State>().states.size
        }
        assertThat(recordedStates).isEqualTo(coins.size)
    }

    @Test
    fun `insert different cash states issued by single transaction`() {
        val nodeIdentity = MEGA_CORP
        val coins = listOf(2.DOLLARS, 1.DOLLARS).map { it.issuedBy(nodeIdentity.ref(1)) }

        //create single transaction with 2 'identical' cash outputs
        val txb = TransactionBuilder(DUMMY_NOTARY)
        coins.map { txb.addOutputState(TransactionState(Cash.State(it, nodeIdentity), Cash.PROGRAM_ID, DUMMY_NOTARY)) }
        txb.addCommand(Cash.Commands.Issue(), nodeIdentity.owningKey)
        val issueTx = txb.toWireTransactionNew(services)

        // ensure transaction contract state is persisted in DBStorage
        val signedIssuedTx = services.signInitialTransaction(txb)
        (services.validatedTransactions as WritableTransactionStorage).addTransaction(signedIssuedTx)

        database.transaction { vaultService.notify(StatesToRecord.ONLY_RELEVANT, issueTx) }

        val recordedStates = database.transaction {
            vaultService.queryBy<Cash.State>().states.size
        }
        assertThat(recordedStates).isEqualTo(coins.size)
    }
}
