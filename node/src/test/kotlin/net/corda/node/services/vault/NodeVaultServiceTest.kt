package net.corda.node.services.vault

import co.paralleluniverse.fibers.Suspendable
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import net.corda.core.contracts.*
import net.corda.core.crypto.NullKeys
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.*
import net.corda.core.internal.NotaryChangeTransactionBuilder
import net.corda.core.internal.packageName
import net.corda.core.node.NotaryInfo
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.*
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.QueryCriteria.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.toNonEmptySet
import net.corda.finance.*
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.utils.sumCash
import net.corda.finance.schemas.CashSchemaV1
import net.corda.finance.workflows.asset.CashUtils
import net.corda.finance.workflows.getCashBalance
import net.corda.node.services.api.WritableTransactionStorage
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.*
import net.corda.testing.internal.LogHelper
import net.corda.testing.internal.vault.*
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestIdentityService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.*
import org.mockito.Mockito.doReturn
import rx.observers.TestSubscriber
import java.math.BigDecimal
import java.security.PublicKey
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import javax.persistence.PersistenceException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NodeVaultServiceTest {
    private companion object {
        val cordappPackages = listOf("net.corda.finance.contracts.asset", CashSchemaV1::class.packageName, "net.corda.testing.contracts",
                "net.corda.testing.internal.vault")
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
        val parameters = testNetworkParameters(notaries = listOf(NotaryInfo(DUMMY_NOTARY, true)))
        val databaseAndServices = MockServices.makeTestDatabaseAndMockServices(
                cordappPackages,
                makeTestIdentityService(MEGA_CORP_IDENTITY, MINI_CORP_IDENTITY, DUMMY_CASH_ISSUER_IDENTITY, DUMMY_NOTARY_IDENTITY),
                megaCorp,
                parameters)
        database = databaseAndServices.first
        services = databaseAndServices.second
        vaultFiller = VaultFiller(services, dummyNotary)
        // This is safe because MockServices only ever have a single identity
        identity = services.myInfo.singleIdentityAndCert()
        issuerServices = MockServices(cordappPackages, dummyCashIssuer, mock(), parameters)
        bocServices = MockServices(cordappPackages, bankOfCorda, mock(), parameters)
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

    class FungibleFoo(override val amount: Amount<Currency>, override val participants: List<AbstractParty>) : FungibleState<Currency>

    @Test(timeout=300_000)
	fun `fungible state selection test`() {
        val issuerParty = services.myInfo.legalIdentities.first()
        val fungibleFoo = FungibleFoo(100.DOLLARS, listOf(issuerParty))
        services.apply {
            val tx = signInitialTransaction(TransactionBuilder(DUMMY_NOTARY).apply {
                addCommand(Command(DummyContract.Commands.Create(), issuerParty.owningKey))
                addOutputState(fungibleFoo, DummyContract.PROGRAM_ID)
            })
            recordTransactions(listOf(tx))
        }

        val baseCriteria: QueryCriteria = QueryCriteria.VaultQueryCriteria(notary = listOf(DUMMY_NOTARY))

        database.transaction {
            val states = services.vaultService.tryLockFungibleStatesForSpending(
                    lockId = UUID.randomUUID(),
                    eligibleStatesQuery = baseCriteria,
                    amount = 10.DOLLARS,
                    contractStateType = FungibleFoo::class.java
            )
            assertEquals(states.single().state.data.amount, 100.DOLLARS)
        }
    }

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
	fun `can query with page size max-integer`() {
        database.transaction {
            vaultFiller.fillWithSomeTestCash(100.DOLLARS, issuerServices, 3, DUMMY_CASH_ISSUER)
        }
        database.transaction {
            val w1 = vaultService.queryBy<Cash.State>(PageSpecification(pageNumber = 1, pageSize = Integer.MAX_VALUE)).states
            assertThat(w1).hasSize(3)
        }
    }

    @Test(timeout=300_000)
	fun `states not local to instance`() {
        database.transaction {
            vaultFiller.fillWithSomeTestCash(100.DOLLARS, issuerServices, 3, DUMMY_CASH_ISSUER)
        }
        database.transaction {
            val w1 = vaultService.queryBy<Cash.State>().states
            assertThat(w1).hasSize(3)

            val originalVault = vaultService
            val services2 = object : MockServices(emptyList(), MEGA_CORP.name, mock()) {
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

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
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
            } catch (e: Exception) {
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
            } catch (e: Exception) {
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

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
    fun `softLockRelease - correctly releases n locked states`() {
        fun queryStates(softLockingType: SoftLockingType) =
            vaultService.queryBy<Cash.State>(VaultQueryCriteria(softLockingCondition = SoftLockingCondition(softLockingType))).states

        database.transaction {
            vaultFiller.fillWithSomeTestCash(100.DOLLARS, issuerServices, 100, DUMMY_CASH_ISSUER)
        }

        val softLockId = UUID.randomUUID()
        val lockCount = NodeVaultService.DEFAULT_SOFT_LOCKING_SQL_IN_CLAUSE_SIZE * 2
        database.transaction {
            assertEquals(100, queryStates(SoftLockingType.UNLOCKED_ONLY).size)
            val unconsumedStates = vaultService.queryBy<Cash.State>().states

            val lockSet = mutableListOf<StateRef>()
            for (i in 0 until lockCount) {
                lockSet.add(unconsumedStates[i].ref)
            }
            vaultService.softLockReserve(softLockId, NonEmptySet.copyOf(lockSet))
            assertEquals(lockCount, queryStates(SoftLockingType.LOCKED_ONLY).size)

            val unlockSet0 = mutableSetOf<StateRef>()
            for (i in 0 until NodeVaultService.DEFAULT_SOFT_LOCKING_SQL_IN_CLAUSE_SIZE + 1) {
                unlockSet0.add(lockSet[i])
            }
            vaultService.softLockRelease(softLockId, NonEmptySet.copyOf(unlockSet0))
            assertEquals(NodeVaultService.DEFAULT_SOFT_LOCKING_SQL_IN_CLAUSE_SIZE - 1, queryStates(SoftLockingType.LOCKED_ONLY).size)

            val unlockSet1 = mutableSetOf<StateRef>()
            for (i in NodeVaultService.DEFAULT_SOFT_LOCKING_SQL_IN_CLAUSE_SIZE + 1 until NodeVaultService.DEFAULT_SOFT_LOCKING_SQL_IN_CLAUSE_SIZE + 3) {
                unlockSet1.add(lockSet[i])
            }
            vaultService.softLockRelease(softLockId, NonEmptySet.copyOf(unlockSet1))
            assertEquals(NodeVaultService.DEFAULT_SOFT_LOCKING_SQL_IN_CLAUSE_SIZE - 1 - 2, queryStates(SoftLockingType.LOCKED_ONLY).size)

            vaultService.softLockRelease(softLockId) // release the rest
            assertEquals(100, queryStates(SoftLockingType.UNLOCKED_ONLY).size)
        }
    }

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
	fun addNoteToTransaction() {
        val megaCorpServices = MockServices(cordappPackages, MEGA_CORP.name, mock(), MEGA_CORP_KEY)
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

    @Test(timeout=300_000)
	fun `is ownable state relevant`() {
        val myAnonymousIdentity = services.keyManagementService.freshKeyAndCert(identity, false)
        val myKeys = services.keyManagementService.filterMyKeys(listOf(identity.owningKey, myAnonymousIdentity.owningKey)).toSet()

        // Well-known owner
        assertTrue { myKeys.isOwnableStateRelevant(identity.party, participants = emptyList()) }
        // Anonymous owner
        assertTrue { myKeys.isOwnableStateRelevant(myAnonymousIdentity.party, participants = emptyList()) }
        // Unknown owner
        assertFalse { myKeys.isOwnableStateRelevant(createUnknownIdentity(), participants = emptyList()) }
        // Under target version 3 only the owner is relevant. This is to preserve backwards compatibility
        assertFalse { myKeys.isOwnableStateRelevant(createUnknownIdentity(), participants = listOf(identity.party)) }
    }

    private fun createUnknownIdentity() = AnonymousParty(generateKeyPair().public)

    private fun Set<PublicKey>.isOwnableStateRelevant(owner: AbstractParty, participants: List<AbstractParty>): Boolean {
        class TestOwnableState : OwnableState {
            override val owner: AbstractParty get() = owner
            override val participants: List<AbstractParty> get() = participants
            override fun withNewOwner(newOwner: AbstractParty): CommandAndState = throw AbstractMethodError()
        }

        return NodeVaultService.isRelevant(TestOwnableState(), this)
    }

    // TODO: Unit test linear state relevancy checks
    @Test(timeout=300_000)
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
        val issueTx = issueBuilder.toWireTransaction(bocServices)
        val cashState = StateAndRef(issueTx.outputs.single(), StateRef(issueTx.id, 0))

        // ensure transaction contract state is persisted in DBStorage
        val signedIssuedTx = services.signInitialTransaction(issueBuilder)
        (services.validatedTransactions as WritableTransactionStorage).addTransaction(signedIssuedTx)

        database.transaction { vaultService.notify(StatesToRecord.ONLY_RELEVANT, issueTx) }
        val expectedIssueUpdate = Vault.Update(emptySet(), setOf(cashState), null)

        val moveTx = database.transaction {
            val moveBuilder = TransactionBuilder(notary).apply {
                CashUtils.generateSpend(services, this, Amount(1000, GBP), identity, thirdPartyIdentity)
            }
            val moveTx = moveBuilder.toWireTransaction(services)
            vaultService.notify(StatesToRecord.ONLY_RELEVANT, moveTx)
            moveTx
        }
        val expectedMoveUpdate = Vault.Update(setOf(cashState), emptySet(), null, consumingTxIds = mapOf(cashState.ref to moveTx.id))

        // ensure transaction contract state is persisted in DBStorage
        val signedMoveTx = services.signInitialTransaction(issueBuilder)
        (services.validatedTransactions as WritableTransactionStorage).addTransaction(signedMoveTx)

        val observedUpdates = vaultSubscriber.onNextEvents
        assertEquals(observedUpdates, listOf(expectedIssueUpdate, expectedMoveUpdate))
    }

    @Test(timeout=300_000)
	fun `correct updates are generated when changing notaries`() {
        val service = vaultService
        val notary = identity.party

        val vaultSubscriber = TestSubscriber<Vault.Update<*>>().apply {
            service.updates.subscribe(this)
        }

        val identity = services.myInfo.singleIdentityAndCert()
        assertEquals(services.identityService.partyFromKey(identity.owningKey), identity.party)
        val anonymousIdentity = services.keyManagementService.freshKeyAndCert(identity, false)
        val thirdPartyServices = MockServices(emptyList(), MEGA_CORP.name, mock<IdentityService>().also {
            doReturn(null).whenever(it).verifyAndRegisterIdentity(argThat { name == MEGA_CORP.name })
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
        val changeNotaryTx = NotaryChangeTransactionBuilder(listOf(initialCashState.ref), issueStx.notary!!, newNotary, services.networkParametersService.currentHash).build()
        val cashStateWithNewNotary = StateAndRef(initialCashState.state.copy(notary = newNotary), StateRef(changeNotaryTx.id, 0))

        database.transaction {
            service.notifyAll(StatesToRecord.ONLY_RELEVANT, listOf(issueStx.tx, changeNotaryTx))
        }

        // ensure transaction contract state is persisted in DBStorage
        (services.validatedTransactions as WritableTransactionStorage).addTransaction(SignedTransaction(changeNotaryTx, listOf(NullKeys.NULL_SIGNATURE)))

        // Move cash
        val moveTxBuilder = database.transaction {
            TransactionBuilder(newNotary).apply {
                CashUtils.generateSpend(services, this, Amount(amount.quantity, GBP), identity, thirdPartyIdentity.party.anonymise())
            }
        }
        val moveTx = moveTxBuilder.toWireTransaction(services)

        // ensure transaction contract state is persisted in DBStorage
        val signedMoveTx = services.signInitialTransaction(moveTxBuilder)
        (services.validatedTransactions as WritableTransactionStorage).addTransaction(signedMoveTx)

        database.transaction {
            service.notify(StatesToRecord.ONLY_RELEVANT, moveTx)
        }

        val expectedIssueUpdate = Vault.Update(emptySet(), setOf(initialCashState), null)
        val expectedNotaryChangeUpdate = Vault.Update(setOf(initialCashState), setOf(cashStateWithNewNotary), null, Vault.UpdateType.NOTARY_CHANGE, consumingTxIds = mapOf(initialCashState.ref to changeNotaryTx.id))
        val expectedMoveUpdate = Vault.Update(setOf(cashStateWithNewNotary), emptySet(), null, consumingTxIds = mapOf(cashStateWithNewNotary.ref to moveTx.id))

        val observedUpdates = vaultSubscriber.onNextEvents
        assertEquals(observedUpdates, listOf(expectedIssueUpdate, expectedNotaryChangeUpdate, expectedMoveUpdate))
    }

    @Test(timeout=300_000)
	fun observerMode() {
        fun countCash(): Long {
            return database.transaction {
                vaultService.queryBy(Cash.State::class.java, QueryCriteria.VaultQueryCriteria(relevancyStatus = Vault.RelevancyStatus.ALL), PageSpecification(1)).totalStatesAvailable
            }
        }
        val currentCashStates = countCash()

        // Send some minimalist dummy transaction.
        val txb = TransactionBuilder(DUMMY_NOTARY)
        txb.addOutputState(Cash.State(MEGA_CORP.ref(0), 100.DOLLARS, MINI_CORP), Cash::class.java.name)
        txb.addCommand(Cash.Commands.Move(), MEGA_CORP_PUBKEY)
        val wtx = txb.toWireTransaction(services)
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

    @Test(timeout=300_000)
	fun `insert equal cash states issued by single transaction`() {
        val nodeIdentity = MEGA_CORP
        val coins = listOf(1.DOLLARS, 1.DOLLARS).map { it.issuedBy(nodeIdentity.ref(1)) }

        //create single transaction with 2 'identical' cash outputs
        val txb = TransactionBuilder(DUMMY_NOTARY)
        coins.map { txb.addOutputState(TransactionState(Cash.State(it, nodeIdentity), Cash.PROGRAM_ID, DUMMY_NOTARY)) }
        txb.addCommand(Cash.Commands.Issue(), nodeIdentity.owningKey)
        val issueTx = txb.toWireTransaction(services)

        // ensure transaction contract state is persisted in DBStorage
        val signedIssuedTx = services.signInitialTransaction(txb)
        (services.validatedTransactions as WritableTransactionStorage).addTransaction(signedIssuedTx)

        database.transaction { vaultService.notify(StatesToRecord.ONLY_RELEVANT, issueTx) }

        val recordedStates = database.transaction {
            vaultService.queryBy<Cash.State>().states.size
        }
        assertThat(recordedStates).isEqualTo(coins.size)
    }

    @Test(timeout=300_000)
	fun `insert different cash states issued by single transaction`() {
        val nodeIdentity = MEGA_CORP
        val coins = listOf(2.DOLLARS, 1.DOLLARS).map { it.issuedBy(nodeIdentity.ref(1)) }

        //create single transaction with 2 'identical' cash outputs
        val txb = TransactionBuilder(DUMMY_NOTARY)
        coins.map { txb.addOutputState(TransactionState(Cash.State(it, nodeIdentity), Cash.PROGRAM_ID, DUMMY_NOTARY)) }
        txb.addCommand(Cash.Commands.Issue(), nodeIdentity.owningKey)
        val issueTx = txb.toWireTransaction(services)

        // ensure transaction contract state is persisted in DBStorage
        val signedIssuedTx = services.signInitialTransaction(txb)
        (services.validatedTransactions as WritableTransactionStorage).addTransaction(signedIssuedTx)

        database.transaction { vaultService.notify(StatesToRecord.ONLY_RELEVANT, issueTx) }

        val recordedStates = database.transaction {
            vaultService.queryBy<Cash.State>().states.size
        }
        assertThat(recordedStates).isEqualTo(coins.size)
    }

    @Test(timeout=300_000)
	fun `test state relevance criteria`() {
        fun createTx(number: Int, vararg participants: Party): SignedTransaction {
            return services.signInitialTransaction(TransactionBuilder(DUMMY_NOTARY).apply {
                addOutputState(DummyState(number, participants.toList()), DummyContract.PROGRAM_ID)
                addCommand(DummyCommandData, listOf(megaCorp.publicKey))
            })
        }

        fun List<StateAndRef<DummyState>>.getNumbers() = map { it.state.data.magicNumber }.toSet()

        services.recordTransactions(StatesToRecord.ONLY_RELEVANT, listOf(createTx(1, megaCorp.party)))
        services.recordTransactions(StatesToRecord.ONLY_RELEVANT, listOf(createTx(2, miniCorp.party)))
        services.recordTransactions(StatesToRecord.ONLY_RELEVANT, listOf(createTx(3, miniCorp.party, megaCorp.party)))
        services.recordTransactions(StatesToRecord.ALL_VISIBLE, listOf(createTx(4, miniCorp.party)))
        services.recordTransactions(StatesToRecord.ALL_VISIBLE, listOf(createTx(5, bankOfCorda.party)))
        services.recordTransactions(StatesToRecord.ALL_VISIBLE, listOf(createTx(6, megaCorp.party, bankOfCorda.party)))
        services.recordTransactions(StatesToRecord.NONE, listOf(createTx(7, bankOfCorda.party)))

        // Test one.
        // RelevancyStatus is ALL by default. This should return five states.
        val resultOne = vaultService.queryBy<DummyState>().states.getNumbers()
        assertEquals(setOf(1, 3, 4, 5, 6), resultOne)

        // Test two.
        // RelevancyStatus set to NOT_RELEVANT.
        val criteriaTwo = VaultQueryCriteria(relevancyStatus = Vault.RelevancyStatus.NOT_RELEVANT)
        val resultTwo = vaultService.queryBy<DummyState>(criteriaTwo).states.getNumbers()
        assertEquals(setOf(4, 5), resultTwo)

        // Test three.
        // RelevancyStatus set to RELEVANT.
        val criteriaThree = VaultQueryCriteria(relevancyStatus = Vault.RelevancyStatus.RELEVANT)
        val resultThree = vaultService.queryBy<DummyState>(criteriaThree).states.getNumbers()
        assertEquals(setOf(1, 3, 6), resultThree)

        // We should never see 2 or 7.
    }

    @Test(timeout=300_000)
	fun `Unique column constraint failing causes linear state to not persist to vault`() {
        fun createTx(): SignedTransaction {
            return services.signInitialTransaction(TransactionBuilder(DUMMY_NOTARY).apply {
                addOutputState(UniqueDummyLinearContract.State(listOf(megaCorp.party), "Dummy linear id"), UNIQUE_DUMMY_LINEAR_CONTRACT_PROGRAM_ID)
                addCommand(DummyCommandData, listOf(megaCorp.publicKey))
            })
        }

        services.recordTransactions(StatesToRecord.ONLY_RELEVANT, listOf(createTx()))
        assertThatExceptionOfType(PersistenceException::class.java).isThrownBy {
            services.recordTransactions(StatesToRecord.ONLY_RELEVANT, listOf(createTx()))
        }
        assertEquals(1, database.transaction {
            vaultService.queryBy<UniqueDummyLinearContract.State>().states.size
        })
    }

    @Test(timeout=300_000)
	fun `Unique column constraint failing causes fungible state to not persist to vault`() {
        fun createTx(): SignedTransaction {
            return services.signInitialTransaction(TransactionBuilder(DUMMY_NOTARY).apply {
                addOutputState(UniqueDummyFungibleContract.State(10.DOLLARS `issued by` DUMMY_CASH_ISSUER, megaCorp.party), UNIQUE_DUMMY_FUNGIBLE_CONTRACT_PROGRAM_ID)
                addCommand(DummyCommandData, listOf(megaCorp.publicKey))
            })
        }

        services.recordTransactions(StatesToRecord.ONLY_RELEVANT, listOf(createTx()))
        assertThatExceptionOfType(PersistenceException::class.java).isThrownBy {
            services.recordTransactions(StatesToRecord.ONLY_RELEVANT, listOf(createTx()))
        }
        assertEquals(1, database.transaction {
            vaultService.queryBy<UniqueDummyFungibleContract.State>().states.size
        })
        assertEquals(10.DOLLARS.quantity, database.transaction {
            vaultService.queryBy<UniqueDummyFungibleContract.State>().states.first().state.data.amount.quantity
        })
    }

    @Test(timeout=300_000)
	fun `Unique column constraint failing causes all states in transaction to fail`() {
        fun createTx(): SignedTransaction {
            return services.signInitialTransaction(TransactionBuilder(DUMMY_NOTARY).apply {
                addOutputState(UniqueDummyLinearContract.State(listOf(megaCorp.party), "Dummy linear id"), UNIQUE_DUMMY_LINEAR_CONTRACT_PROGRAM_ID)
                addOutputState(DummyDealContract.State(listOf(megaCorp.party), "Dummy linear id"), DUMMY_DEAL_PROGRAM_ID)
                addCommand(DummyCommandData, listOf(megaCorp.publicKey))
            })
        }

        services.recordTransactions(StatesToRecord.ONLY_RELEVANT, listOf(createTx()))
        assertThatExceptionOfType(PersistenceException::class.java).isThrownBy {
            services.recordTransactions(StatesToRecord.ONLY_RELEVANT, listOf(createTx()))
        }
        assertEquals(1, database.transaction {
            vaultService.queryBy<UniqueDummyLinearContract.State>().states.size
        })
        assertEquals(1, database.transaction {
            vaultService.queryBy<DummyDealContract.State>().states.size
        })
    }

    @Test(timeout=300_000)
	fun `Vault queries return all states by default`() {
        fun createTx(number: Int, vararg participants: Party): SignedTransaction {
            return services.signInitialTransaction(TransactionBuilder(DUMMY_NOTARY).apply {
                addOutputState(DummyState(number, participants.toList()), DummyContract.PROGRAM_ID)
                addCommand(DummyCommandData, listOf(megaCorp.publicKey))
            })
        }

        fun List<StateAndRef<DummyState>>.getNumbers() = map { it.state.data.magicNumber }.toSet()

        services.recordTransactions(StatesToRecord.ONLY_RELEVANT, listOf(createTx(1, megaCorp.party)))
        services.recordTransactions(StatesToRecord.ONLY_RELEVANT, listOf(createTx(2, miniCorp.party)))
        services.recordTransactions(StatesToRecord.ONLY_RELEVANT, listOf(createTx(3, miniCorp.party, megaCorp.party)))
        services.recordTransactions(StatesToRecord.ALL_VISIBLE, listOf(createTx(4, miniCorp.party)))
        services.recordTransactions(StatesToRecord.ALL_VISIBLE, listOf(createTx(5, bankOfCorda.party)))
        services.recordTransactions(StatesToRecord.ALL_VISIBLE, listOf(createTx(6, megaCorp.party, bankOfCorda.party)))
        services.recordTransactions(StatesToRecord.NONE, listOf(createTx(7, bankOfCorda.party)))

        // Test one.
        // RelevancyStatus is ALL by default. This should return five states.
        val resultOne = vaultService.queryBy<DummyState>().states.getNumbers()
        assertEquals(setOf(1, 3, 4, 5, 6), resultOne)

        // We should never see 2 or 7.
    }

    @Test(timeout=300_000)
@Ignore
    fun `trackByCriteria filters updates and snapshots`() {
        /*
         * This test is ignored as the functionality it tests is not yet implemented - see CORDA-2389
         */
        fun addCashToVault() {
            database.transaction {
                vaultFiller.fillWithSomeTestCash(100.DOLLARS, issuerServices, 1, DUMMY_CASH_ISSUER)
            }
        }

        fun addDummyToVault() {
            database.transaction {
                vaultFiller.fillWithDummyState()
            }
        }
        addCashToVault()
        addDummyToVault()
        val criteria = VaultQueryCriteria(contractStateTypes = setOf(Cash.State::class.java))
        val data = vaultService.trackBy<ContractState>(criteria)
        for (state in data.snapshot.states) {
            assertEquals(Cash.PROGRAM_ID, state.state.contract)
        }

        val allCash = data.updates.all {
            it.produced.all {
                it.state.contract == Cash.PROGRAM_ID
            }
        }

        addCashToVault()
        addDummyToVault()
        addCashToVault()
        allCash.subscribe {
            assertTrue(it)
        }
    }

    @Test(timeout=300_000)
	fun `test concurrent update of contract state type mappings`() {
        // no registered contract state types at start-up.
        assertEquals(0, vaultService.contractStateTypeMappings.size)

        fun makeCash(amount: Amount<Currency>, issuer: AbstractParty, depositRef: Byte = 1) =
                StateAndRef(
                        TransactionState(Cash.State(amount `issued by` issuer.ref(depositRef), identity.party), Cash.PROGRAM_ID, DUMMY_NOTARY, constraint = AlwaysAcceptAttachmentConstraint),
                        StateRef(SecureHash.randomSHA256(), Random().nextInt(32))
                )

        val cashIssued = setOf<StateAndRef<ContractState>>(makeCash(100.DOLLARS, dummyCashIssuer.party))
        val cashUpdate = Vault.Update(emptySet(), cashIssued)

        val service = Executors.newFixedThreadPool(10)
        (1..100).map {
            service.submit {
                database.transaction {
                    vaultService.publishUpdates.onNext(cashUpdate)
                }
            }
        }.forEach { it.getOrThrow() }

        vaultService.contractStateTypeMappings.forEach {
            println("${it.key} = ${it.value}")
        }
        // Cash.State and its superclasses and interfaces: FungibleAsset, FungibleState, OwnableState, QueryableState
        assertEquals(4, vaultService.contractStateTypeMappings.size)

        service.shutdown()
    }
}
