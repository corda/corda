package net.corda.node.services.vault

import net.corda.core.ThreadBox
import net.corda.core.bufferUntilSubscribed
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.messaging.DataFeed
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultQueryException
import net.corda.core.node.services.VaultQueryService
import net.corda.core.node.services.vault.*
import net.corda.core.node.services.vault.QueryCriteria.VaultCustomQueryCriteria
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.storageKryo
import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor
import net.corda.node.services.database.HibernateConfiguration
import net.corda.node.services.vault.schemas.jpa.VaultSchemaV1
import org.jetbrains.exposed.sql.transactions.TransactionManager
import rx.subjects.PublishSubject
import java.lang.Exception
import java.util.*
import javax.persistence.EntityManager
import javax.persistence.Tuple


class HibernateVaultQueryImpl(hibernateConfig: HibernateConfiguration,
                              val updatesPublisher: PublishSubject<Vault.Update>) : SingletonSerializeAsToken(), VaultQueryService {
    companion object {
        val log = loggerFor<HibernateVaultQueryImpl>()
    }

    private val sessionFactory = hibernateConfig.sessionFactoryForRegisteredSchemas()
    private val criteriaBuilder = sessionFactory.criteriaBuilder

    @Throws(VaultQueryException::class)
    override fun <T : ContractState> _queryBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort, contractType: Class<out T>): Vault.Page<T> {
        log.info("Vault Query for contract type: $contractType, criteria: $criteria, pagination: $paging, sorting: $sorting")

        // calculate total results where a page specification has been defined
        var totalStates = 0L
        if (!paging.isDefault) {
            val count = builder { VaultSchemaV1.VaultStates::recordedTime.count() }
            val countCriteria = VaultCustomQueryCriteria(count)
            val results = queryBy(contractType, criteria.and(countCriteria))
            totalStates = results.otherResults[0] as Long
        }

        val session = sessionFactory.withOptions().
                connection(TransactionManager.current().connection).
                openSession()

        session.use {
            val criteriaQuery = criteriaBuilder.createQuery(Tuple::class.java)
            val queryRootVaultStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)

            val contractTypeMappings = resolveUniqueContractStateTypes(session)
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
                    if (paging.pageNumber < 0) throw VaultQueryException("Page specification: invalid page number ${paging.pageNumber} [page numbers start from 0]")
                    if (paging.pageSize < 0 || paging.pageSize > MAX_PAGE_SIZE) throw VaultQueryException("Page specification: invalid page size ${paging.pageSize} [maximum page size is ${MAX_PAGE_SIZE}]")

                    if ((paging.pageNumber != 0) && (paging.pageSize * paging.pageNumber >= totalStates))
                        throw VaultQueryException("Requested more results than available [${paging.pageSize} * ${paging.pageNumber} >= $totalStates]")
                }

                query.firstResult = paging.pageNumber * paging.pageSize
                query.maxResults = paging.pageSize + 1  // detection too many results

                // execution
                val results = query.resultList

                // final pagination check (fail-fast on too many results when no pagination specified)
                if (paging.isDefault && results.size > DEFAULT_PAGE_SIZE)
                    throw VaultQueryException("Please specify a `PageSpecification` as there are more results [${results.size}] than the default page size [$DEFAULT_PAGE_SIZE]")

                val statesAndRefs: MutableList<StateAndRef<*>> = mutableListOf()
                val statesMeta: MutableList<Vault.StateMetadata> = mutableListOf()
                val otherResults: MutableList<Any> = mutableListOf()

                results.asSequence()
                        .forEachIndexed { index, result ->
                            if (result[0] is VaultSchemaV1.VaultStates) {
                                if (!paging.isDefault && index == paging.pageSize) // skip last result if paged
                                    return@forEachIndexed
                                val it = result[0] as VaultSchemaV1.VaultStates
                                val stateRef = StateRef(SecureHash.parse(it.stateRef!!.txId!!), it.stateRef!!.index!!)
                                val state = it.contractState.deserialize<TransactionState<T>>(storageKryo())
                                statesMeta.add(Vault.StateMetadata(stateRef, it.contractStateClassName, it.recordedTime, it.consumedTime, it.stateStatus, it.notaryName, it.notaryKey, it.lockId, it.lockUpdateTime))
                                statesAndRefs.add(StateAndRef(state, stateRef))
                            }
                            else {
                                log.debug { "OtherResults: ${Arrays.toString(result.toArray())}" }
                                otherResults.addAll(result.toArray().asList())
                            }
                        }

                return Vault.Page(states = statesAndRefs, statesMetadata = statesMeta, pageable = paging, stateTypes = criteriaParser.stateTypes, totalStatesAvailable = totalStates, otherResults = otherResults) as Vault.Page<T>

            } catch (e: Exception) {
                log.error(e.message)
                throw e.cause ?: e
            }
        }
    }

    private val mutex = ThreadBox({ updatesPublisher })

    @Throws(VaultQueryException::class)
    override fun <T : ContractState> _trackBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort, contractType: Class<out T>): DataFeed<Vault.Page<T>, Vault.Update> {
        return mutex.locked {
            val snapshotResults = _queryBy<T>(criteria, paging, sorting, contractType)
            val updates = updatesPublisher.bufferUntilSubscribed().filter { it.containsType(contractType, snapshotResults.stateTypes) }
            DataFeed(snapshotResults, updates)
        }
    }

    /**
     * Maintain a list of contract state interfaces to concrete types stored in the vault
     * for usage in generic queries of type queryBy<LinearState> or queryBy<FungibleState<*>>
     */
    fun resolveUniqueContractStateTypes(session: EntityManager): Map<String, List<String>> {
        val criteria = criteriaBuilder.createQuery(String::class.java)
        val vaultStates = criteria.from(VaultSchemaV1.VaultStates::class.java)
        criteria.select(vaultStates.get("contractStateClassName")).distinct(true)
        val query = session.createQuery(criteria)
        val results = query.resultList
        val distinctTypes = results.map { it }

        val contractInterfaceToConcreteTypes = mutableMapOf<String, MutableList<String>>()
        distinctTypes.forEach { it ->
            val concreteType = Class.forName(it) as Class<ContractState>
            val contractInterfaces = deriveContractInterfaces(concreteType)
            contractInterfaces.map {
                val contractInterface = contractInterfaceToConcreteTypes.getOrPut(it.name, { mutableListOf() })
                contractInterface.add(concreteType.name)
            }
        }
        return contractInterfaceToConcreteTypes
    }

    private fun <T : ContractState> deriveContractInterfaces(clazz: Class<T>): Set<Class<T>> {
        val myInterfaces: MutableSet<Class<T>> = mutableSetOf()
        clazz.interfaces.forEach {
            if (!it.equals(ContractState::class.java)) {
                myInterfaces.add(it as Class<T>)
                myInterfaces.addAll(deriveContractInterfaces(it))
            }
        }
        return myInterfaces
    }
}