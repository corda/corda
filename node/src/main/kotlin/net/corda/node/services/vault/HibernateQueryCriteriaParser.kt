package net.corda.node.services.vault

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultQueryException
import net.corda.core.node.services.vault.*
import net.corda.core.node.services.vault.QueryCriteria.CommonQueryCriteria
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.toHexString
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.trace
import net.corda.node.services.vault.schemas.jpa.CommonSchemaV1
import net.corda.node.services.vault.schemas.jpa.VaultSchemaV1
import org.bouncycastle.asn1.x500.X500Name
import java.util.*
import javax.persistence.Tuple
import javax.persistence.criteria.*


class HibernateQueryCriteriaParser(val contractType: Class<out ContractState>,
                                   val contractTypeMappings: Map<String, List<String>>,
                                   val criteriaBuilder: CriteriaBuilder,
                                   val criteriaQuery: CriteriaQuery<Tuple>,
                                   val vaultStates: Root<VaultSchemaV1.VaultStates>) : IQueryCriteriaParser {
    private companion object {
        val log = loggerFor<HibernateQueryCriteriaParser>()
    }

    // incrementally build list of join predicates
    private val joinPredicates = mutableListOf<Predicate>()
    // incrementally build list of root entities (for later use in Sort parsing)
    private val rootEntities = mutableMapOf<Class<out PersistentState>, Root<*>>()
    private val aggregateExpressions = mutableListOf<Expression<*>>()

    var stateTypes: Vault.StateStatus = Vault.StateStatus.UNCONSUMED

    override fun parseCriteria(criteria: QueryCriteria.VaultQueryCriteria) : Collection<Predicate> {
        log.trace { "Parsing VaultQueryCriteria: $criteria" }
        val predicateSet = mutableSetOf<Predicate>()

        // contract State Types
        val combinedContractTypeTypes = criteria.contractStateTypes?.plus(contractType) ?: setOf(contractType)
        combinedContractTypeTypes.filter { it.name != ContractState::class.java.name }.let {
            val interfaces = it.flatMap { contractTypeMappings[it.name] ?: emptyList() }
            val concrete = it.filter { !it.isInterface }.map { it.name }
            val all = interfaces.plus(concrete)
            if (all.isNotEmpty())
                predicateSet.add(criteriaBuilder.and(vaultStates.get<String>("contractStateClassName").`in`(all)))
        }

        // soft locking
        if (!criteria.includeSoftlockedStates)
            predicateSet.add(criteriaBuilder.and(vaultStates.get<String>("lockId").isNull))

        // notary names
        criteria.notaryName?.let {
            val notaryNames = (criteria.notaryName as List<X500Name>).map { it.toString() }
            predicateSet.add(criteriaBuilder.and(vaultStates.get<String>("notaryName").`in`(notaryNames)))
        }

        // state references
        criteria.stateRefs?.let {
            val persistentStateRefs = (criteria.stateRefs as List<StateRef>).map { PersistentStateRef(it.txhash.bytes.toHexString(), it.index) }
            val compositeKey = vaultStates.get<PersistentStateRef>("stateRef")
            predicateSet.add(criteriaBuilder.and(compositeKey.`in`(persistentStateRefs)))
        }

        // time constraints (recorded, consumed)
        criteria.timeCondition?.let {
            val timeCondition = criteria.timeCondition
            val timeInstantType = timeCondition!!.type
            val timeColumn = when (timeInstantType) {
                QueryCriteria.TimeInstantType.RECORDED -> Column.Kotlin(VaultSchemaV1.VaultStates::recordedTime)
                QueryCriteria.TimeInstantType.CONSUMED -> Column.Kotlin(VaultSchemaV1.VaultStates::consumedTime)
            }
            val expression = CriteriaExpression.ColumnPredicateExpression(timeColumn, timeCondition.predicate)
            predicateSet.add(parseExpression(vaultStates, expression) as Predicate)
        }
        return predicateSet
    }

    private fun columnPredicateToPredicate(column: Path<out Any?>, columnPredicate: ColumnPredicate<*>): Predicate {
        return when (columnPredicate) {
            is ColumnPredicate.EqualityComparison -> {
                val literal = columnPredicate.rightLiteral
                when (columnPredicate.operator) {
                    EqualityComparisonOperator.EQUAL -> criteriaBuilder.equal(column, literal)
                    EqualityComparisonOperator.NOT_EQUAL -> criteriaBuilder.notEqual(column, literal)
                }
            }
            is ColumnPredicate.BinaryComparison -> {
                column as Path<Comparable<Any?>?>
                val literal = columnPredicate.rightLiteral as Comparable<Any?>?
                when (columnPredicate.operator) {
                    BinaryComparisonOperator.GREATER_THAN -> criteriaBuilder.greaterThan(column, literal)
                    BinaryComparisonOperator.GREATER_THAN_OR_EQUAL -> criteriaBuilder.greaterThanOrEqualTo(column, literal)
                    BinaryComparisonOperator.LESS_THAN -> criteriaBuilder.lessThan(column, literal)
                    BinaryComparisonOperator.LESS_THAN_OR_EQUAL -> criteriaBuilder.lessThanOrEqualTo(column, literal)
                }
            }
            is ColumnPredicate.Likeness -> {
                column as Path<String?>
                when (columnPredicate.operator) {
                    LikenessOperator.LIKE -> criteriaBuilder.like(column, columnPredicate.rightLiteral)
                    LikenessOperator.NOT_LIKE -> criteriaBuilder.notLike(column, columnPredicate.rightLiteral)
                }
            }
            is ColumnPredicate.CollectionExpression -> {
                when (columnPredicate.operator) {
                    CollectionOperator.IN -> column.`in`(columnPredicate.rightLiteral)
                    CollectionOperator.NOT_IN -> criteriaBuilder.not(column.`in`(columnPredicate.rightLiteral))
                }
            }
            is ColumnPredicate.Between -> {
                column as Path<Comparable<Any?>?>
                val fromLiteral = columnPredicate.rightFromLiteral as Comparable<Any?>?
                val toLiteral = columnPredicate.rightToLiteral as Comparable<Any?>?
                criteriaBuilder.between(column, fromLiteral, toLiteral)
            }
            is ColumnPredicate.NullExpression -> {
                when (columnPredicate.operator) {
                    NullOperator.IS_NULL -> criteriaBuilder.isNull(column)
                    NullOperator.NOT_NULL -> criteriaBuilder.isNotNull(column)
                }
            }
            else -> throw VaultQueryException("Not expecting $columnPredicate")
        }
    }

    private fun <O> parseExpression(entityRoot: Root<O>, expression: CriteriaExpression<O, Boolean>, predicateSet: MutableSet<Predicate>) {
        if (expression is CriteriaExpression.AggregateFunctionExpression<O,*>) {
            parseAggregateFunction(entityRoot, expression)
        } else {
            predicateSet.add(parseExpression(entityRoot, expression) as Predicate)
        }
    }

    private fun <O, R> parseExpression(root: Root<O>, expression: CriteriaExpression<O, R>): Expression<Boolean> {
        return when (expression) {
            is CriteriaExpression.BinaryLogical -> {
                val leftPredicate = parseExpression(root, expression.left)
                val rightPredicate = parseExpression(root, expression.right)
                when (expression.operator) {
                    BinaryLogicalOperator.AND -> criteriaBuilder.and(leftPredicate, rightPredicate)
                    BinaryLogicalOperator.OR -> criteriaBuilder.or(leftPredicate, rightPredicate)
                }
            }
            is CriteriaExpression.Not -> criteriaBuilder.not(parseExpression(root, expression.expression))
            is CriteriaExpression.ColumnPredicateExpression<O, *> -> {
                val column = root.get<Any?>(getColumnName(expression.column))
                columnPredicateToPredicate(column, expression.predicate)
            }
            else -> throw VaultQueryException("Unexpected expression: $expression")
        }
    }

    private fun <O, R> parseAggregateFunction(root: Root<O>, expression: CriteriaExpression.AggregateFunctionExpression<O, R>): Expression<out Any?>? {
        val column = root.get<Any?>(getColumnName(expression.column))
        val columnPredicate = expression.predicate
        when (columnPredicate) {
            is ColumnPredicate.AggregateFunction -> {
                column as Path<Long?>?
                val aggregateExpression =
                    when (columnPredicate.type) {
                        AggregateFunctionType.SUM -> criteriaBuilder.sum(column)
                        AggregateFunctionType.AVG -> criteriaBuilder.avg(column)
                        AggregateFunctionType.COUNT -> criteriaBuilder.count(column)
                        AggregateFunctionType.MAX -> criteriaBuilder.max(column)
                        AggregateFunctionType.MIN -> criteriaBuilder.min(column)
                    }
                aggregateExpressions.add(aggregateExpression)
                // optionally order by this aggregate function
                expression.orderBy?.let {
                    val orderCriteria =
                        when (expression.orderBy!!) {
                            Sort.Direction.ASC -> criteriaBuilder.asc(aggregateExpression)
                            Sort.Direction.DESC -> criteriaBuilder.desc(aggregateExpression)
                        }
                    criteriaQuery.orderBy(orderCriteria)
                }
                // add optional group by clauses
                expression.groupByColumns?.let { columns ->
                    val groupByExpressions =
                            columns.map { column ->
                                val path = root.get<Any?>(getColumnName(column))
                                aggregateExpressions.add(path)
                                path
                            }
                    criteriaQuery.groupBy(groupByExpressions)
                }
                return aggregateExpression
            }
            else -> throw VaultQueryException("Not expecting $columnPredicate")
        }
    }

    override fun parseCriteria(criteria: QueryCriteria.FungibleAssetQueryCriteria) : Collection<Predicate> {
        log.trace { "Parsing FungibleAssetQueryCriteria: $criteria" }

        var predicateSet = mutableSetOf<Predicate>()

        val vaultFungibleStates = criteriaQuery.from(VaultSchemaV1.VaultFungibleStates::class.java)
        rootEntities.putIfAbsent(VaultSchemaV1.VaultFungibleStates::class.java, vaultFungibleStates)

        val joinPredicate = criteriaBuilder.equal(vaultStates.get<PersistentStateRef>("stateRef"), vaultFungibleStates.get<PersistentStateRef>("stateRef"))
        predicateSet.add(joinPredicate)

        // owner
        criteria.owner?.let {
            val ownerKeys = criteria.owner as List<AbstractParty>
            val joinFungibleStateToParty = vaultFungibleStates.join<VaultSchemaV1.VaultFungibleStates, CommonSchemaV1.Party>("issuerParty")
            val owners = ownerKeys.map { it.nameOrNull()?.toString() ?: it.toString()}
            predicateSet.add(criteriaBuilder.and(joinFungibleStateToParty.get<CommonSchemaV1.Party>("name").`in`(owners)))
        }

        // quantity
        criteria.quantity?.let {
            predicateSet.add(columnPredicateToPredicate(vaultFungibleStates.get<Long>("quantity"), it))
        }

        // issuer party
        criteria.issuerPartyName?.let {
            val issuerParties = criteria.issuerPartyName as List<AbstractParty>
            val joinFungibleStateToParty = vaultFungibleStates.join<VaultSchemaV1.VaultFungibleStates, CommonSchemaV1.Party>("issuerParty")
            val dealPartyKeys = issuerParties.map { it.nameOrNull().toString() }
            predicateSet.add(criteriaBuilder.equal(joinFungibleStateToParty.get<CommonSchemaV1.Party>("name"), dealPartyKeys))
        }

        // issuer reference
        criteria.issuerRef?.let {
            val issuerRefs = (criteria.issuerRef as List<OpaqueBytes>).map { it.bytes }
            predicateSet.add(criteriaBuilder.and(vaultFungibleStates.get<ByteArray>("issuerRef").`in`(issuerRefs)))
        }

        // participants
        criteria.participants?.let {
            val participants = criteria.participants as List<AbstractParty>
            val joinFungibleStateToParty = vaultFungibleStates.join<VaultSchemaV1.VaultFungibleStates, CommonSchemaV1.Party>("participants")
            val participantKeys = participants.map { it.nameOrNull().toString() }
            predicateSet.add(criteriaBuilder.and(joinFungibleStateToParty.get<CommonSchemaV1.Party>("name").`in`(participantKeys)))
            criteriaQuery.distinct(true)
        }
        return predicateSet
    }

    override fun parseCriteria(criteria: QueryCriteria.LinearStateQueryCriteria) : Collection<Predicate> {
        log.trace { "Parsing LinearStateQueryCriteria: $criteria" }

        val predicateSet = mutableSetOf<Predicate>()

        val vaultLinearStates = criteriaQuery.from(VaultSchemaV1.VaultLinearStates::class.java)
        rootEntities.putIfAbsent(VaultSchemaV1.VaultLinearStates::class.java, vaultLinearStates)

        val joinPredicate = criteriaBuilder.equal(vaultStates.get<PersistentStateRef>("stateRef"), vaultLinearStates.get<PersistentStateRef>("stateRef"))
        joinPredicates.add(joinPredicate)

        // linear ids
        criteria.linearId?.let {
            val uniqueIdentifiers = criteria.linearId as List<UniqueIdentifier>
            val externalIds = uniqueIdentifiers.mapNotNull { it.externalId }
            if (externalIds.isNotEmpty())
                predicateSet.add(criteriaBuilder.and(vaultLinearStates.get<String>("externalId").`in`(externalIds)))
            predicateSet.add(criteriaBuilder.and(vaultLinearStates.get<UUID>("uuid").`in`(uniqueIdentifiers.map { it.id })))
        }

        // deal refs
        criteria.dealRef?.let {
            val dealRefs = criteria.dealRef as List<String>
            predicateSet.add(criteriaBuilder.and(vaultLinearStates.get<String>("dealReference").`in`(dealRefs)))
        }

        // deal participants
        criteria.participants?.let {
            val participants = criteria.participants as List<AbstractParty>
            val joinLinearStateToParty = vaultLinearStates.join<VaultSchemaV1.VaultLinearStates, CommonSchemaV1.Party>("participants")
            val participantKeys = participants.map { it.nameOrNull().toString() }
            predicateSet.add(criteriaBuilder.and(joinLinearStateToParty.get<CommonSchemaV1.Party>("name").`in`(participantKeys)))
            criteriaQuery.distinct(true)
        }
        return predicateSet
    }

    override fun <L : PersistentState> parseCriteria(criteria: QueryCriteria.VaultCustomQueryCriteria<L>): Collection<Predicate> {
        log.trace { "Parsing VaultCustomQueryCriteria: $criteria" }

        val predicateSet = mutableSetOf<Predicate>()
        val entityClass = resolveEnclosingObjectFromExpression(criteria.expression)

        try {
            val entityRoot = criteriaQuery.from(entityClass)
            rootEntities.putIfAbsent(entityClass, entityRoot)

            val joinPredicate = criteriaBuilder.equal(vaultStates.get<PersistentStateRef>("stateRef"), entityRoot.get<PersistentStateRef>("stateRef"))
            joinPredicates.add(joinPredicate)

            // resolve general criteria expressions
            parseExpression(entityRoot, criteria.expression, predicateSet)
        }
        catch (e: Exception) {
            e.message?.let { message ->
                if (message.contains("Not an entity"))
                    throw VaultQueryException("""
                    Please register the entity '${entityClass.name.substringBefore('$')}' class in your CorDapp's CordaPluginRegistry configuration (requiredSchemas attribute)
                    and ensure you have declared (in supportedSchemas()) and mapped (in generateMappedObject()) the schema in the associated contract state's QueryableState interface implementation.
                    See https://docs.corda.net/persistence.html?highlight=persistence for more information""")
            }
            throw VaultQueryException("Parsing error: ${e.message}")
        }
        return predicateSet
    }

    override fun parseOr(left: QueryCriteria, right: QueryCriteria): Collection<Predicate> {
        log.trace { "Parsing OR QueryCriteria composition: $left OR $right" }

        var predicateSet = mutableSetOf<Predicate>()
        val leftPredicates = parse(left)
        val rightPredicates = parse(right)

        val orPredicate = criteriaBuilder.or(*leftPredicates.toTypedArray(), *rightPredicates.toTypedArray())
        predicateSet.add(orPredicate)

        return predicateSet
    }

    override fun parseAnd(left: QueryCriteria, right: QueryCriteria): Collection<Predicate> {
        log.trace { "Parsing AND QueryCriteria composition: $left AND $right" }

        var predicateSet = mutableSetOf<Predicate>()
        val leftPredicates = parse(left)
        val rightPredicates = parse(right)

        val andPredicate = criteriaBuilder.and(*leftPredicates.toTypedArray(), *rightPredicates.toTypedArray())
        predicateSet.add(andPredicate)

        return predicateSet
    }

    override fun parse(criteria: QueryCriteria, sorting: Sort?): Collection<Predicate> {
        val predicateSet = criteria.visit(this)

        sorting?.let {
            if (sorting.columns.isNotEmpty())
                parse(sorting)
        }

        val selections =
            if (aggregateExpressions.isEmpty())
                listOf(vaultStates).plus(rootEntities.map { it.value })
            else
                aggregateExpressions
        criteriaQuery.multiselect(selections)
        val combinedPredicates = joinPredicates.plus(predicateSet)
        criteriaQuery.where(*combinedPredicates.toTypedArray())

        return predicateSet
    }

    override fun parseCriteria(criteria: CommonQueryCriteria): Collection<Predicate> {
        log.trace { "Parsing CommonQueryCriteria: $criteria" }
        val predicateSet = mutableSetOf<Predicate>()

        // state status
        stateTypes = criteria.status
        if (criteria.status != Vault.StateStatus.ALL)
            predicateSet.add(criteriaBuilder.equal(vaultStates.get<Vault.StateStatus>("stateStatus"), criteria.status))

        return predicateSet
    }

    private fun parse(sorting: Sort) {
        log.trace { "Parsing sorting specification: $sorting" }

        var orderCriteria = mutableListOf<Order>()

        sorting.columns.map { (sortAttribute, direction) ->
            val (entityStateClass, entityStateAttributeParent, entityStateAttributeChild) =
                    when(sortAttribute) {
                        is SortAttribute.Standard -> parse(sortAttribute.attribute)
                        is SortAttribute.Custom -> Triple(sortAttribute.entityStateClass, sortAttribute.entityStateColumnName, null)
                    }
            val sortEntityRoot =
                    rootEntities.getOrElse(entityStateClass) {
                        // scenario where sorting on attributes not parsed as criteria
                        val entityRoot = criteriaQuery.from(entityStateClass)
                        rootEntities.put(entityStateClass, entityRoot)
                        val joinPredicate = criteriaBuilder.equal(vaultStates.get<PersistentStateRef>("stateRef"), entityRoot.get<PersistentStateRef>("stateRef"))
                        joinPredicates.add(joinPredicate)
                        entityRoot
                    }
            when (direction) {
                Sort.Direction.ASC -> {
                    if (entityStateAttributeChild != null)
                        orderCriteria.add(criteriaBuilder.asc(sortEntityRoot.get<String>(entityStateAttributeParent).get<String>(entityStateAttributeChild)))
                    else
                        orderCriteria.add(criteriaBuilder.asc(sortEntityRoot.get<String>(entityStateAttributeParent)))
                }
                Sort.Direction.DESC ->
                    if (entityStateAttributeChild != null)
                        orderCriteria.add(criteriaBuilder.desc(sortEntityRoot.get<String>(entityStateAttributeParent).get<String>(entityStateAttributeChild)))
                    else
                        orderCriteria.add(criteriaBuilder.desc(sortEntityRoot.get<String>(entityStateAttributeParent)))
            }
        }
        if (orderCriteria.isNotEmpty()) {
            criteriaQuery.orderBy(orderCriteria)
            criteriaQuery.where(*joinPredicates.toTypedArray())
        }
    }

    private fun parse(sortAttribute: Sort.Attribute): Triple<Class<out PersistentState>, String, String?> {
        val entityClassAndColumnName : Triple<Class<out PersistentState>, String, String?> =
            when(sortAttribute) {
                is Sort.CommonStateAttribute -> {
                    Triple(VaultSchemaV1.VaultStates::class.java, sortAttribute.attributeParent, sortAttribute.attributeChild)
                }
                is Sort.VaultStateAttribute -> {
                    Triple(VaultSchemaV1.VaultStates::class.java, sortAttribute.attributeName, null)
                }
                is Sort.LinearStateAttribute -> {
                    Triple(VaultSchemaV1.VaultLinearStates::class.java, sortAttribute.attributeName, null)
                }
                is Sort.FungibleStateAttribute -> {
                    Triple(VaultSchemaV1.VaultFungibleStates::class.java, sortAttribute.attributeName, null)
                }
                else -> throw VaultQueryException("Invalid sort attribute: $sortAttribute")
            }
        return entityClassAndColumnName
    }
}