package net.corda.node.services.vault

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.ThreadBox
import net.corda.core.internal.bufferUntilSubscribed
import net.corda.core.messaging.DataFeed
import net.corda.core.node.services.*
import net.corda.core.node.services.vault.*
import net.corda.core.node.services.vault.QueryCriteria.VaultCustomQueryCriteria
import net.corda.core.serialization.SerializationDefaults.STORAGE_CONTEXT
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.toNonEmptySet
import net.corda.core.utilities.trace
import net.corda.node.services.persistence.HibernateConfiguration
import net.corda.node.utilities.DatabaseTransactionManager
import org.hibernate.Session
import rx.Observable
import java.lang.Exception
import java.util.*
import javax.persistence.Tuple

class HibernateVaultQueryImpl(val hibernateConfig: HibernateConfiguration,
                              val vault: VaultService) : SingletonSerializeAsToken(), VaultQueryService {
    companion object {
        val log = loggerFor<HibernateVaultQueryImpl>()
    }

    private var sessionFactory = hibernateConfig.sessionFactoryForRegisteredSchemas()
    private var criteriaBuilder = sessionFactory.criteriaBuilder

    /**
     * Maintain a list of contract state interfaces to concrete types stored in the vault
     * for usage in generic queries of type queryBy<LinearState> or queryBy<FungibleState<*>>
     */
    private val contractTypeMappings = bootstrapContractStateTypes()

    init {
        vault.rawUpdates.subscribe { update ->
            update.produced.forEach {
                val concreteType = it.state.data.javaClass
                log.trace { "State update of type: $concreteType" }
                val seen = contractTypeMappings.any { it.value.contains(concreteType.name) }
                if (!seen) {
                    val contractInterfaces = deriveContractInterfaces(concreteType)
                    contractInterfaces.map {
                        val contractInterface = contractTypeMappings.getOrPut(it.name, { mutableSetOf() })
                        contractInterface.add(concreteType.name)
                    }
                }
            }
        }
    }

    @Throws(VaultQueryException::class)
    override fun <T : ContractState> _queryBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort, contractType: Class<out T>): Vault.Page<T> {
        log.info("Vault Query for contract type: $contractType, criteria: $criteria, pagination: $paging, sorting: $sorting")

        // refresh to include any schemas registered after initial VQ service initialisation
        sessionFactory = hibernateConfig.sessionFactoryForRegisteredSchemas()
        criteriaBuilder = sessionFactory.criteriaBuilder

        // calculate total results where a page specification has been defined
        var totalStates = -1L
        if (!paging.isDefault) {
            val count = builder { VaultSchemaV1.VaultStates::recordedTime.count() }
            val countCriteria = VaultCustomQueryCriteria(count, Vault.StateStatus.ALL)
            val results = queryBy(contractType, criteria.and(countCriteria))
            totalStates = results.otherResults[0] as Long
        }

        val session = getSession()

        session.use {
            val criteriaQuery = criteriaBuilder.createQuery(Tuple::class.java)
            val queryRootVaultStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)

            // TODO: revisit (use single instance of parser for all queries)
            val criteriaParser = HibernateQueryCriteriaParser(contractType, contractTypeMappings, criteriaBuilder, criteriaQuery, queryRootVaultStates)

            try {
                // parse criteria and build where predicates
                criteriaParser.parse(criteria, sorting)

                // prepare query for execution
                val query = session.createQuery(criteriaQuery)

                // pagination checks
                if (!paging.isDefault) {
                    // pagination
                    if (paging.pageNumber < DEFAULT_PAGE_NUM) throw VaultQueryException("Page specification: invalid page number ${paging.pageNumber} [page numbers start from $DEFAULT_PAGE_NUM]")
                    if (paging.pageSize < 1) throw VaultQueryException("Page specification: invalid page size ${paging.pageSize} [must be a value between 1 and $MAX_PAGE_SIZE]")
                }

                query.firstResult = (paging.pageNumber - 1) * paging.pageSize
                query.maxResults = paging.pageSize + 1  // detection too many results

                // execution
                val results = query.resultList

                // final pagination check (fail-fast on too many results when no pagination specified)
                if (paging.isDefault && results.size > DEFAULT_PAGE_SIZE)
                    throw VaultQueryException("Please specify a `PageSpecification` as there are more results [${results.size}] than the default page size [$DEFAULT_PAGE_SIZE]")

                val statesAndRefs: MutableList<StateAndRef<T>> = mutableListOf()
                val statesMeta: MutableList<Vault.StateMetadata> = mutableListOf()
                val otherResults: MutableList<Any> = mutableListOf()

                results.asSequence()
                        .forEachIndexed { index, result ->
                            if (result[0] is VaultSchemaV1.VaultStates) {
                                if (!paging.isDefault && index == paging.pageSize) // skip last result if paged
                                    return@forEachIndexed
                                val vaultState = result[0] as VaultSchemaV1.VaultStates
                                val stateRef = StateRef(SecureHash.parse(vaultState.stateRef!!.txId!!), vaultState.stateRef!!.index!!)
                                val state = vaultState.contractState.deserialize<TransactionState<T>>(context = STORAGE_CONTEXT)
                                statesMeta.add(Vault.StateMetadata(stateRef,
                                                                   vaultState.contractStateClassName,
                                                                   vaultState.recordedTime,
                                                                   vaultState.consumedTime,
                                                                   vaultState.stateStatus,
                                                                   vaultState.notary,
                                                                   vaultState.lockId,
                                                                   vaultState.lockUpdateTime))
                                statesAndRefs.add(StateAndRef(state, stateRef))
                            }
                            else {
                                // TODO: improve typing of returned other results
                                log.debug { "OtherResults: ${Arrays.toString(result.toArray())}" }
                                otherResults.addAll(result.toArray().asList())
                            }
                        }

                return Vault.Page(states = statesAndRefs, statesMetadata = statesMeta, stateTypes = criteriaParser.stateTypes, totalStatesAvailable = totalStates, otherResults = otherResults)
            } catch (e: Exception) {
                log.error(e.message)
                throw e.cause ?: e
            }
        }
    }

    private val mutex = ThreadBox({ vault.updatesPublisher })

    @Throws(VaultQueryException::class)
    override fun <T : ContractState> _trackBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort, contractType: Class<out T>): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        return mutex.locked {
            val snapshotResults = _queryBy(criteria, paging, sorting, contractType)
            @Suppress("UNCHECKED_CAST")
            val updates = vault.updatesPublisher.bufferUntilSubscribed().filter { it.containsType(contractType, snapshotResults.stateTypes) } as Observable<Vault.Update<T>>
            DataFeed(snapshotResults, updates)
        }
    }

    private fun getSession(): Session {
        return sessionFactory.withOptions().
                connection(DatabaseTransactionManager.current().connection).
                openSession()
    }

    /**
     * Derive list from existing vault states and then incrementally update using vault observables
     */
    fun bootstrapContractStateTypes(): MutableMap<String, MutableSet<String>> {
        val criteria = criteriaBuilder.createQuery(String::class.java)
        val vaultStates = criteria.from(VaultSchemaV1.VaultStates::class.java)
        criteria.select(vaultStates.get("contractStateClassName")).distinct(true)
        val session = getSession()
        session.use {
            val query = session.createQuery(criteria)
            val results = query.resultList
            val distinctTypes = results.map { it }

            val contractInterfaceToConcreteTypes = mutableMapOf<String, MutableSet<String>>()
            distinctTypes.forEach { type ->
                @Suppress("UNCHECKED_CAST")
                val concreteType = Class.forName(type) as Class<ContractState>
                val contractInterfaces = deriveContractInterfaces(concreteType)
                contractInterfaces.map {
                    val contractInterface = contractInterfaceToConcreteTypes.getOrPut(it.name, { mutableSetOf() })
                    contractInterface.add(concreteType.name)
                }
            }
            return contractInterfaceToConcreteTypes
        }
    }

    private fun <T : ContractState> deriveContractInterfaces(clazz: Class<T>): Set<Class<T>> {
        val myInterfaces: MutableSet<Class<T>> = mutableSetOf()
        clazz.interfaces.forEach {
            if (!it.equals(ContractState::class.java)) {
                @Suppress("UNCHECKED_CAST")
                myInterfaces.add(it as Class<T>)
                myInterfaces.addAll(deriveContractInterfaces(it))
            }
        }
        return myInterfaces
    }

    @Suspendable
    @Throws(StatesNotAvailableException::class)
    override fun <T : FungibleAsset<U>, U : Any> tryLockFungibleStatesForSpending(lockId: UUID,
                                                                                  eligibleStatesQuery: QueryCriteria,
                                                                                  amount: Amount<U>,
                                                                                  contractType: Class<out T>): List<StateAndRef<T>> {
        if (amount.quantity == 0L) {
            return emptyList()
        }
        // Enrich QueryCriteria with additional default attributes (such as soft locks)
        val sortAttribute = SortAttribute.Standard(Sort.CommonStateAttribute.STATE_REF)
        val sorter = Sort(setOf(Sort.SortColumn(sortAttribute, Sort.Direction.ASC)))
        val enrichedCriteria = QueryCriteria.VaultQueryCriteria(
                contractStateTypes = setOf(contractType),
                softLockingCondition = QueryCriteria.SoftLockingCondition(QueryCriteria.SoftLockingType.UNLOCKED_AND_SPECIFIED, listOf(lockId)))
        val results = queryBy(contractType, enrichedCriteria.and(eligibleStatesQuery), sorter)
        var claimedAmount = 0L
        val claimedStates = mutableListOf<StateAndRef<T>>()
        for (state in results.states) {
            val issuedAssetToken = state.state.data.amount.token
            if (issuedAssetToken.product == amount.token) {
                claimedStates += state
                claimedAmount += state.state.data.amount.quantity
                if (claimedAmount > amount.quantity) {
                    break
                }
            }
        }
        if (claimedStates.isEmpty() || claimedAmount < amount.quantity) {
            return emptyList()
        }
        vault.softLockReserve(lockId, claimedStates.map { it.ref }.toNonEmptySet())
        return claimedStates
    }
}