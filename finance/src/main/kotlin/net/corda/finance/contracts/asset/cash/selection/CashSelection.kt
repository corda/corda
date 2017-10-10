package net.corda.finance.contracts.asset.cash.selection

import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.Strand
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.StatesNotAvailableException
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.deserialize
import net.corda.core.utilities.*
import net.corda.finance.contracts.asset.Cash
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Pluggable interface to allow for different cash selection provider implementations
 * Default implementation [CashSelectionH2Impl] uses H2 database and a custom function within H2 to perform aggregation.
 * Custom implementations must implement this interface and declare their implementation in
 * META-INF/services/net.corda.contracts.asset.CashSelection
 */
abstract class CashSelection {
    companion object {
        val instance = AtomicReference<CashSelection>()

        fun getInstance(metadata: () -> java.sql.DatabaseMetaData): CashSelection {
            return instance.get() ?: {
                val _metadata = metadata()
                val cashSelectionAlgos = ServiceLoader.load(CashSelection::class.java).toList()
                val cashSelectionAlgo = cashSelectionAlgos.firstOrNull { it.isCompatible(_metadata) }
                cashSelectionAlgo?.let {
                    instance.set(cashSelectionAlgo)
                    cashSelectionAlgo
                } ?: throw ClassNotFoundException("\nUnable to load compatible cash selection algorithm implementation for JDBC driver ($_metadata)." +
                        "\nPlease specify an implementation in META-INF/services/net.corda.finance.contracts.asset.CashSelection")
            }.invoke()
        }

        val log = loggerFor<CashSelection>()
    }

    // coin selection retry loop counter, sleep (msecs) and lock for selecting states
    private val MAX_RETRIES = 5
    private val RETRY_SLEEP = 100
    private val spendLock: ReentrantLock = ReentrantLock()

    /**
     * Upon dynamically loading configured Cash Selection algorithms declared in META-INF/services
     * this method determines whether the loaded implementation is compatible and usable with the currently
     * loaded JDBC driver.
     * Note: the first loaded implementation to pass this check will be used at run-time.
     */
    abstract fun isCompatible(metadata: DatabaseMetaData): Boolean

    /**
     * A vendor specific query(ies) to gather Cash states that are available.
     * @param statement The service hub to allow access to the database session
     * @param amount The amount of currency desired (ignoring issues, but specifying the currency)
     * @param lockId The FlowLogic.runId.uuid of the flow, which is used to soft reserve the states.
     * Also, previous outputs of the flow will be eligible as they are implicitly locked with this id until the flow completes.
     * @param notary If null the notary source is ignored, if specified then only states marked
     * with this notary are included.
     * @param issuerKeysStr Optional issuer key to match against.
     * @param issuerRefsStr Optional issuer reference to match against.
     * @return JDBC ResultSet with the matching states that were found. If sufficient funds were found these will be locked,
     * otherwise what is available is returned unlocked for informational purposes.
     */
    abstract fun executeQuery(statement: Statement, amount: Amount<Currency>, lockId: UUID, notary: Party?,
                              issuerKeysStr: String?, issuerRefsStr: String?) : ResultSet

    override abstract fun toString() : String

    /**
     * Query to gather Cash states that are available
     * @param services The service hub to allow access to the database session
     * @param amount The amount of currency desired (ignoring issues, but specifying the currency)
     * @param onlyFromIssuerParties If empty the operation ignores the specifics of the issuer,
     * otherwise the set of eligible states wil be filtered to only include those from these issuers.
     * @param notary If null the notary source is ignored, if specified then only states marked
     * with this notary are included.
     * @param lockId The FlowLogic.runId.uuid of the flow, which is used to soft reserve the states.
     * Also, previous outputs of the flow will be eligible as they are implicitly locked with this id until the flow completes.
     * @param withIssuerRefs If not empty the specific set of issuer references to match against.
     * @return The matching states that were found. If sufficient funds were found these will be locked,
     * otherwise what is available is returned unlocked for informational purposes.
     */
    @Suspendable
    fun unconsumedCashStatesForSpending(services: ServiceHub,
                                        amount: Amount<Currency>,
                                        onlyFromIssuerParties: Set<AbstractParty> = emptySet(),
                                        notary: Party? = null,
                                        lockId: UUID,
                                        withIssuerRefs: Set<OpaqueBytes> = emptySet()): List<StateAndRef<Cash.State>> {
        val issuerKeysStr : String? =
                if (onlyFromIssuerParties.isNotEmpty())
                    onlyFromIssuerParties.fold("") { left, right -> left + "('${right.owningKey.toBase58String()}')," }.dropLast(1)
                else null
        val issuerRefsStr : String? =
                if (withIssuerRefs.isNotEmpty())
                    withIssuerRefs.fold("") { left, right -> left + "('${right.bytes.toHexString()}')," }.dropLast(1)
                else null
        val stateAndRefs = mutableListOf<StateAndRef<Cash.State>>()


        for (retryCount in 1..MAX_RETRIES) {

            spendLock.withLock {
                val statement = services.jdbcSession().createStatement()
                try {

                    // we select spendable states irrespective of lock but prioritised by unlocked ones (Eg. null)
                    // the softLockReserve update will detect whether we try to lock states locked by others
                    val rs = executeQuery(statement, amount, lockId, notary, issuerKeysStr, issuerRefsStr)
                    stateAndRefs.clear()

                    var totalPennies = 0L
                    while (rs.next()) {
                        val txHash = SecureHash.parse(rs.getString(1))
                        val index = rs.getInt(2)
                        val stateRef = StateRef(txHash, index)
                        val state = rs.getBytes(3).deserialize<TransactionState<Cash.State>>(context = SerializationDefaults.STORAGE_CONTEXT)
                        val pennies = rs.getLong(4)
                        totalPennies = rs.getLong(5)
                        val rowLockId = rs.getString(6)
                        stateAndRefs.add(StateAndRef(state, stateRef))
                        log.trace { "ROW: $rowLockId ($lockId): $stateRef : $pennies ($totalPennies)" }
                    }

                    if (stateAndRefs.isNotEmpty() && totalPennies >= amount.quantity) {
                        // we should have a minimum number of states to satisfy our selection `amount` criteria
                        log.trace("Coin selection for $amount retrieved ${stateAndRefs.count()} states totalling $totalPennies pennies: $stateAndRefs")

                        // With the current single threaded state machine available states are guaranteed to lock.
                        // TODO However, we will have to revisit these methods in the future multi-threaded.
                        services.vaultService.softLockReserve(lockId, (stateAndRefs.map { it.ref }).toNonEmptySet())
                        return stateAndRefs
                    }
                    log.trace("Coin selection requested $amount but retrieved $totalPennies pennies with state refs: ${stateAndRefs.map { it.ref }}")
                    // retry as more states may become available
                } catch (e: SQLException) {
                    log.error("""Failed retrieving unconsumed states for: amount [$amount], onlyFromIssuerParties [$onlyFromIssuerParties], notary [$notary], lockId [$lockId]
                        $e.
                    """)
                } catch (e: StatesNotAvailableException) { // Should never happen with single threaded state machine
                    stateAndRefs.clear()
                    log.warn(e.message)
                    // retry only if there are locked states that may become available again (or consumed with change)
                } finally {
                    statement.close()
                }
            }

            log.warn("Coin selection failed on attempt $retryCount")
            // TODO: revisit the back off strategy for contended spending.
            if (retryCount != MAX_RETRIES) {
                Strand.sleep(RETRY_SLEEP * retryCount.toLong())
            }
        }

        log.warn("Insufficient spendable states identified for $amount")
        return if(amount.quantity>0) stateAndRefs else if(amount.quantity<=0) stateAndRefs else stateAndRefs
    }
}
