package net.corda.core.node.services

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.FungibleAsset
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowException
import net.corda.core.messaging.DataFeed
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import java.util.*

interface VaultQueryService {

    // DOCSTART VaultQueryAPI
    /**
     * Generic vault query function which takes a [QueryCriteria] object to define filters,
     * optional [PageSpecification] and optional [Sort] modification criteria (default unsorted),
     * and returns a [Vault.Page] object containing the following:
     *  1. states as a List of <StateAndRef> (page number and size defined by [PageSpecification])
     *  2. states metadata as a List of [Vault.StateMetadata] held in the Vault States table.
     *  3. total number of results available if [PageSpecification] supplied (otherwise returns -1)
     *  4. status types used in this query: UNCONSUMED, CONSUMED, ALL
     *  5. other results (aggregate functions with/without using value groups)
     *
     * @throws VaultQueryException if the query cannot be executed for any reason
     *        (missing criteria or parsing error, paging errors, unsupported query, underlying database error)
     *
     * Notes
     *   If no [PageSpecification] is provided, a maximum of [DEFAULT_PAGE_SIZE] results will be returned.
     *   API users must specify a [PageSpecification] if they are expecting more than [DEFAULT_PAGE_SIZE] results,
     *   otherwise a [VaultQueryException] will be thrown alerting to this condition.
     *   It is the responsibility of the API user to request further pages and/or specify a more suitable [PageSpecification].
     */
    @Throws(VaultQueryException::class)
    fun <T : ContractState> _queryBy(criteria: QueryCriteria,
                                     paging: PageSpecification,
                                     sorting: Sort,
                                     contractType: Class<out T>): Vault.Page<T>

    /**
     * Generic vault query function which takes a [QueryCriteria] object to define filters,
     * optional [PageSpecification] and optional [Sort] modification criteria (default unsorted),
     * and returns a [Vault.PageAndUpdates] object containing
     * 1) a snapshot as a [Vault.Page] (described previously in [queryBy])
     * 2) an [Observable] of [Vault.Update]
     *
     * @throws VaultQueryException if the query cannot be executed for any reason
     *
     * Notes: the snapshot part of the query adheres to the same behaviour as the [queryBy] function.
     *        the [QueryCriteria] applies to both snapshot and deltas (streaming updates).
     */
    @Throws(VaultQueryException::class)
    fun <T : ContractState> _trackBy(criteria: QueryCriteria,
                                     paging: PageSpecification,
                                     sorting: Sort,
                                     contractType: Class<out T>): DataFeed<Vault.Page<T>, Vault.Update<T>>
    // DOCEND VaultQueryAPI

    // Note: cannot apply @JvmOverloads to interfaces nor interface implementations
    // Java Helpers
    fun <T : ContractState> queryBy(contractType: Class<out T>): Vault.Page<T> {
        return _queryBy(QueryCriteria.VaultQueryCriteria(), PageSpecification(), Sort(emptySet()), contractType)
    }

    fun <T : ContractState> queryBy(contractType: Class<out T>, criteria: QueryCriteria): Vault.Page<T> {
        return _queryBy(criteria, PageSpecification(), Sort(emptySet()), contractType)
    }

    fun <T : ContractState> queryBy(contractType: Class<out T>, criteria: QueryCriteria, paging: PageSpecification): Vault.Page<T> {
        return _queryBy(criteria, paging, Sort(emptySet()), contractType)
    }

    fun <T : ContractState> queryBy(contractType: Class<out T>, criteria: QueryCriteria, sorting: Sort): Vault.Page<T> {
        return _queryBy(criteria, PageSpecification(), sorting, contractType)
    }

    fun <T : ContractState> queryBy(contractType: Class<out T>, criteria: QueryCriteria, paging: PageSpecification, sorting: Sort): Vault.Page<T> {
        return _queryBy(criteria, paging, sorting, contractType)
    }

    fun <T : ContractState> trackBy(contractType: Class<out T>): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        return _trackBy(QueryCriteria.VaultQueryCriteria(), PageSpecification(), Sort(emptySet()), contractType)
    }

    fun <T : ContractState> trackBy(contractType: Class<out T>, criteria: QueryCriteria): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        return _trackBy(criteria, PageSpecification(), Sort(emptySet()), contractType)
    }

    fun <T : ContractState> trackBy(contractType: Class<out T>, criteria: QueryCriteria, paging: PageSpecification): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        return _trackBy(criteria, paging, Sort(emptySet()), contractType)
    }

    fun <T : ContractState> trackBy(contractType: Class<out T>, criteria: QueryCriteria, sorting: Sort): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        return _trackBy(criteria, PageSpecification(), sorting, contractType)
    }

    fun <T : ContractState> trackBy(contractType: Class<out T>, criteria: QueryCriteria, paging: PageSpecification, sorting: Sort): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        return _trackBy(criteria, paging, sorting, contractType)
    }

    /**
     * Helper function to combine using [VaultQueryService] calls to determine spendable states and soft locking them.
     * Currently performance will be worse than for the hand optimised version in `Cash.unconsumedCashStatesForSpending`
     * However, this is fully generic and can operate with custom [FungibleAsset] states.
     * @param lockId The [FlowLogic.runId.uuid] of the current flow used to soft lock the states.
     * @param eligibleStatesQuery A custom query object that selects down to the appropriate subset of all states of the
     * [contractType]. e.g. by selecting on account, issuer, etc. The query is internally augmented with the UNCONSUMED,
     * soft lock and contract type requirements.
     * @param amount The required amount of the asset, but with the issuer stripped off.
     * It is assumed that compatible issuer states will be filtered out by the [eligibleStatesQuery].
     * @param contractType class type of the result set.
     * @return Returns a locked subset of the [eligibleStatesQuery] sufficient to satisfy the requested amount,
     * or else an empty list and no change in the stored lock states when their are insufficient resources available.
     */
    @Suspendable
    @Throws(StatesNotAvailableException::class)
    fun <T : FungibleAsset<U>, U : Any> tryLockFungibleStatesForSpending(lockId: UUID,
                                                                         eligibleStatesQuery: QueryCriteria,
                                                                         amount: Amount<U>,
                                                                         contractType: Class<out T>): List<StateAndRef<T>>
}

inline fun <reified T : ContractState> VaultQueryService.queryBy(): Vault.Page<T> {
    return _queryBy(QueryCriteria.VaultQueryCriteria(), PageSpecification(), Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultQueryService.queryBy(criteria: QueryCriteria): Vault.Page<T> {
    return _queryBy(criteria, PageSpecification(), Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultQueryService.queryBy(criteria: QueryCriteria, paging: PageSpecification): Vault.Page<T> {
    return _queryBy(criteria, paging, Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultQueryService.queryBy(criteria: QueryCriteria, sorting: Sort): Vault.Page<T> {
    return _queryBy(criteria, PageSpecification(), sorting, T::class.java)
}

inline fun <reified T : ContractState> VaultQueryService.queryBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort): Vault.Page<T> {
    return _queryBy(criteria, paging, sorting, T::class.java)
}

inline fun <reified T : ContractState> VaultQueryService.trackBy(): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(QueryCriteria.VaultQueryCriteria(), PageSpecification(), Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultQueryService.trackBy(criteria: QueryCriteria): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria, PageSpecification(), Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultQueryService.trackBy(criteria: QueryCriteria, paging: PageSpecification): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria, paging, Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultQueryService.trackBy(criteria: QueryCriteria, sorting: Sort): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria, PageSpecification(), sorting, T::class.java)
}

inline fun <reified T : ContractState> VaultQueryService.trackBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort): DataFeed<Vault.Page<T>, Vault.Update<T>> {
    return _trackBy(criteria, paging, sorting, T::class.java)
}

class VaultQueryException(description: String) : FlowException(description)