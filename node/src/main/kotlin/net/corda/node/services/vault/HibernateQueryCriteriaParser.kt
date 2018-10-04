package net.corda.node.services.vault

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.identity.AbstractParty
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultQueryException
import net.corda.core.node.services.vault.*
import net.corda.core.node.services.vault.QueryCriteria.CommonQueryCriteria
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import net.corda.node.services.persistence.NodeAttachmentService
import org.hibernate.query.criteria.internal.expression.LiteralExpression
import org.hibernate.query.criteria.internal.path.SingularAttributePath
import org.hibernate.query.criteria.internal.predicate.ComparisonPredicate
import org.hibernate.query.criteria.internal.predicate.InPredicate
import java.time.Instant
import java.util.*
import javax.persistence.Tuple
import javax.persistence.criteria.*


abstract class AbstractQueryCriteriaParser<Q : GenericQueryCriteria<Q,P>, in P: BaseQueryCriteriaParser<Q, P, S>, in S: BaseSort> : BaseQueryCriteriaParser<Q, P, S> {

    abstract val criteriaBuilder: CriteriaBuilder

    override fun parseOr(left: Q, right: Q): Collection<Predicate> {
        val predicateSet = mutableSetOf<Predicate>()
        val leftPredicates = parse(left)
        val rightPredicates = parse(right)

        val leftAnd = criteriaBuilder.and(*leftPredicates.toTypedArray())
        val rightAnd = criteriaBuilder.and(*rightPredicates.toTypedArray())
        val orPredicate = criteriaBuilder.or(leftAnd,rightAnd)
        predicateSet.add(orPredicate)

        return predicateSet
    }

    override fun parseAnd(left: Q, right: Q): Collection<Predicate> {
        val predicateSet = mutableSetOf<Predicate>()
        val leftPredicates = parse(left)
        val rightPredicates = parse(right)

        val andPredicate = criteriaBuilder.and(*leftPredicates.toTypedArray(), *rightPredicates.toTypedArray())
        predicateSet.add(andPredicate)

        return predicateSet
    }

    protected fun columnPredicateToPredicate(column: Path<out Any?>, columnPredicate: ColumnPredicate<*>): Predicate {
        return when (columnPredicate) {
            is ColumnPredicate.EqualityComparison -> {
                val literal = columnPredicate.rightLiteral
                when (columnPredicate.operator) {
                    EqualityComparisonOperator.EQUAL -> criteriaBuilder.equal(column, literal)
                    EqualityComparisonOperator.NOT_EQUAL -> criteriaBuilder.notEqual(column, literal)
                }
            }
            is ColumnPredicate.BinaryComparison -> {
                val literal: Comparable<Any?>? = uncheckedCast(columnPredicate.rightLiteral)
                @Suppress("UNCHECKED_CAST")
                column as Path<Comparable<Any?>?>
                when (columnPredicate.operator) {
                    BinaryComparisonOperator.GREATER_THAN -> criteriaBuilder.greaterThan(column, literal)
                    BinaryComparisonOperator.GREATER_THAN_OR_EQUAL -> criteriaBuilder.greaterThanOrEqualTo(column, literal)
                    BinaryComparisonOperator.LESS_THAN -> criteriaBuilder.lessThan(column, literal)
                    BinaryComparisonOperator.LESS_THAN_OR_EQUAL -> criteriaBuilder.lessThanOrEqualTo(column, literal)
                }
            }
            is ColumnPredicate.Likeness -> {
                @Suppress("UNCHECKED_CAST")
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
                @Suppress("UNCHECKED_CAST")
                column as Path<Comparable<Any?>?>
                val fromLiteral: Comparable<Any?>? = uncheckedCast(columnPredicate.rightFromLiteral)
                val toLiteral: Comparable<Any?>? = uncheckedCast(columnPredicate.rightToLiteral)
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
}

class HibernateAttachmentQueryCriteriaParser(override val criteriaBuilder: CriteriaBuilder,
                                             private val criteriaQuery: CriteriaQuery<NodeAttachmentService.DBAttachment>, val root: Root<NodeAttachmentService.DBAttachment>) :
        AbstractQueryCriteriaParser<AttachmentQueryCriteria, AttachmentsQueryCriteriaParser, AttachmentSort>(), AttachmentsQueryCriteriaParser {

    private companion object {
        private val log = contextLogger()
    }

    init {
        criteriaQuery.select(root)
    }

    override fun parse(criteria: AttachmentQueryCriteria, sorting: AttachmentSort?): Collection<Predicate> {
        val predicateSet = criteria.visit(this)

        sorting?.let {
            if (sorting.columns.isNotEmpty())
                parse(sorting)
        }

        criteriaQuery.where(*predicateSet.toTypedArray())

        return predicateSet
    }

    private fun parse(sorting: AttachmentSort) {
        log.trace { "Parsing sorting specification: $sorting" }

        val orderCriteria = mutableListOf<Order>()

        sorting.columns.map { (sortAttribute, direction) ->
            when (direction) {
                Sort.Direction.ASC -> orderCriteria.add(criteriaBuilder.asc(root.get<String>(sortAttribute.columnName)))
                Sort.Direction.DESC -> orderCriteria.add(criteriaBuilder.desc(root.get<String>(sortAttribute.columnName)))
            }
        }
        if (orderCriteria.isNotEmpty()) {
            criteriaQuery.orderBy(orderCriteria)
        }
    }

    override fun parseCriteria(criteria: AttachmentQueryCriteria.AttachmentsQueryCriteria): Collection<Predicate> {
        log.trace { "Parsing AttachmentsQueryCriteria: $criteria" }

        val predicateSet = mutableSetOf<Predicate>()

        criteria.filenameCondition?.let {
            predicateSet.add(columnPredicateToPredicate(root.get<String>("filename"), it))
        }

        criteria.uploaderCondition?.let {
            predicateSet.add(columnPredicateToPredicate(root.get<String>("uploader"), it))
        }

        criteria.uploadDateCondition?.let {
            predicateSet.add(columnPredicateToPredicate(root.get<Instant>("upload_date"), it))
        }

        return predicateSet
    }
}

class HibernateQueryCriteriaParser(val contractStateType: Class<out ContractState>,
                                   val contractStateTypeMappings: Map<String, Set<String>>,
                                   override val criteriaBuilder: CriteriaBuilder,
                                   val criteriaQuery: CriteriaQuery<Tuple>,
                                   val vaultStates: Root<VaultSchemaV1.VaultStates>) : AbstractQueryCriteriaParser<QueryCriteria, IQueryCriteriaParser, Sort>(), IQueryCriteriaParser {
    private companion object {
        private val log = contextLogger()
    }

    // incrementally build list of root entities (for later use in Sort parsing)
    private val rootEntities = mutableMapOf<Class<out PersistentState>, Root<*>>(Pair(VaultSchemaV1.VaultStates::class.java, vaultStates))
    private val aggregateExpressions = mutableListOf<Expression<*>>()
    private val commonPredicates = mutableMapOf<Pair<String, Operator>, Predicate>()   // schema attribute Name, operator -> predicate

    var stateTypes: Vault.StateStatus = Vault.StateStatus.UNCONSUMED

    override fun parseCriteria(criteria: QueryCriteria.VaultQueryCriteria): Collection<Predicate> {
        log.trace { "Parsing VaultQueryCriteria: $criteria" }
        val predicateSet = mutableSetOf<Predicate>()

        // soft locking
        criteria.softLockingCondition?.let {
            val softLocking = criteria.softLockingCondition
            val type = softLocking!!.type
            when (type) {
                QueryCriteria.SoftLockingType.UNLOCKED_ONLY ->
                    predicateSet.add(criteriaBuilder.and(vaultStates.get<String>("lockId").isNull))
                QueryCriteria.SoftLockingType.LOCKED_ONLY ->
                    predicateSet.add(criteriaBuilder.and(vaultStates.get<String>("lockId").isNotNull))
                QueryCriteria.SoftLockingType.UNLOCKED_AND_SPECIFIED -> {
                    require(softLocking.lockIds.isNotEmpty()) { "Must specify one or more lockIds" }
                    predicateSet.add(criteriaBuilder.or(vaultStates.get<String>("lockId").isNull,
                            vaultStates.get<String>("lockId").`in`(softLocking.lockIds.map { it.toString() })))
                }
                QueryCriteria.SoftLockingType.SPECIFIED -> {
                    require(softLocking.lockIds.isNotEmpty()) { "Must specify one or more lockIds" }
                    predicateSet.add(criteriaBuilder.and(vaultStates.get<String>("lockId").`in`(softLocking.lockIds.map { it.toString() })))
                }
            }
        }

        // notary names
        criteria.notary?.let {
            predicateSet.add(criteriaBuilder.and(vaultStates.get<AbstractParty>("notary").`in`(criteria.notary)))
        }

        // state references
        criteria.stateRefs?.let {
            val persistentStateRefs = (criteria.stateRefs as List<StateRef>).map(::PersistentStateRef)
            val compositeKey = vaultStates.get<PersistentStateRef>("stateRef")
            predicateSet.add(criteriaBuilder.and(compositeKey.`in`(persistentStateRefs)))
        }

        // time constraints (recorded, consumed)
        criteria.timeCondition?.let {
            val timeCondition = criteria.timeCondition
            val timeInstantType = timeCondition!!.type
            val timeColumn = when (timeInstantType) {
                QueryCriteria.TimeInstantType.RECORDED -> Column(VaultSchemaV1.VaultStates::recordedTime)
                QueryCriteria.TimeInstantType.CONSUMED -> Column(VaultSchemaV1.VaultStates::consumedTime)
            }
            val expression = CriteriaExpression.ColumnPredicateExpression(timeColumn, timeCondition.predicate)
            predicateSet.add(parseExpression(vaultStates, expression) as Predicate)
        }
        return predicateSet
    }

    private fun deriveContractStateTypes(contractStateTypes: Set<Class<out ContractState>>? = null): Set<String> {
        log.trace { "Contract types to be derived: primary ($contractStateType), additional ($contractStateTypes)" }
        val combinedContractStateTypes = contractStateTypes?.plus(contractStateType) ?: setOf(contractStateType)
        combinedContractStateTypes.filter { it.name != ContractState::class.java.name }.let {
            val interfaces = it.flatMap { contractStateTypeMappings[it.name] ?: setOf(it.name) }
            val concrete = it.filter { !it.isInterface }.map { it.name }
            log.trace { "Derived contract types: ${interfaces.union(concrete)}" }
            return interfaces.union(concrete)
        }
    }


    private fun <O> parseExpression(entityRoot: Root<O>, expression: CriteriaExpression<O, Boolean>, predicateSet: MutableSet<Predicate>) {
        if (expression is CriteriaExpression.AggregateFunctionExpression<O, *>) {
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
                @Suppress("UNCHECKED_CAST")
                column as Path<Long?>?
                val aggregateExpression =
                        when (columnPredicate.type) {
                            AggregateFunctionType.SUM -> criteriaBuilder.sum(column)
                            AggregateFunctionType.AVG -> criteriaBuilder.avg(column)
                            AggregateFunctionType.COUNT -> criteriaBuilder.count(column)
                            AggregateFunctionType.MAX -> criteriaBuilder.max(column)
                            AggregateFunctionType.MIN -> criteriaBuilder.min(column)
                        }
                //TODO investigate possibility to avoid producing redundant joins in SQL for multiple aggregate functions against the same table
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
                            columns.map { _column ->
                                val path = root.get<Any?>(getColumnName(_column))
                                if (path is SingularAttributePath) //remove the same columns from different joins to match the single column in 'group by' only (from the last join)
                                    aggregateExpressions.removeAll {
                                        elem -> if (elem is SingularAttributePath) elem.attribute.javaMember == path.attribute.javaMember else false
                                    }
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

    override fun parseCriteria(criteria: QueryCriteria.FungibleAssetQueryCriteria): Collection<Predicate> {
        log.trace { "Parsing FungibleAssetQueryCriteria: $criteria" }

        val predicateSet = mutableSetOf<Predicate>()

        // ensure we re-use any existing instance of the same root entity
        val entityStateClass = VaultSchemaV1.VaultFungibleStates::class.java
        val vaultFungibleStates =
                rootEntities.getOrElse(entityStateClass) {
                    val entityRoot = criteriaQuery.from(entityStateClass)
                    rootEntities[entityStateClass] = entityRoot
                    entityRoot
                }

        val joinPredicate = criteriaBuilder.equal(vaultStates.get<PersistentStateRef>("stateRef"), vaultFungibleStates.get<PersistentStateRef>("stateRef"))
        predicateSet.add(joinPredicate)

        // owner
        criteria.owner?.let {
            val owners = criteria.owner as List<AbstractParty>
            predicateSet.add(criteriaBuilder.and(vaultFungibleStates.get<AbstractParty>("owner").`in`(owners)))
        }

        // quantity
        criteria.quantity?.let {
            predicateSet.add(columnPredicateToPredicate(vaultFungibleStates.get<Long>("quantity"), it))
        }

        // issuer party
        criteria.issuer?.let {
            val issuerParties = criteria.issuer as List<AbstractParty>
            predicateSet.add(criteriaBuilder.and(vaultFungibleStates.get<AbstractParty>("issuer").`in`(issuerParties)))
        }

        // issuer reference
        criteria.issuerRef?.let {
            val issuerRefs = (criteria.issuerRef as List<OpaqueBytes>).map { it.bytes }
            predicateSet.add(criteriaBuilder.and(vaultFungibleStates.get<ByteArray>("issuerRef").`in`(issuerRefs)))
        }

        // participants
        criteria.participants?.let {
            val participants = criteria.participants as List<AbstractParty>
            val joinLinearStateToParty = vaultFungibleStates.joinSet<VaultSchemaV1.VaultFungibleStates, AbstractParty>("participants")
            predicateSet.add(criteriaBuilder.and(joinLinearStateToParty.`in`(participants)))
            criteriaQuery.distinct(true)
        }
        return predicateSet
    }

    override fun parseCriteria(criteria: QueryCriteria.LinearStateQueryCriteria): Collection<Predicate> {
        log.trace { "Parsing LinearStateQueryCriteria: $criteria" }

        val predicateSet = mutableSetOf<Predicate>()

        // ensure we re-use any existing instance of the same root entity
        val entityStateClass = VaultSchemaV1.VaultLinearStates::class.java
        val vaultLinearStates =
                rootEntities.getOrElse(entityStateClass) {
                    val entityRoot = criteriaQuery.from(entityStateClass)
                    rootEntities[entityStateClass] = entityRoot
                    entityRoot
                }

        val joinPredicate = criteriaBuilder.equal(vaultStates.get<PersistentStateRef>("stateRef"), vaultLinearStates.get<PersistentStateRef>("stateRef"))
        predicateSet.add(joinPredicate)

        // linear ids UUID
        criteria.uuid?.let {
            val uuids = criteria.uuid as List<UUID>
            predicateSet.add(criteriaBuilder.and(vaultLinearStates.get<UUID>("uuid").`in`(uuids)))
        }

        // linear ids externalId
        criteria.externalId?.let {
            val externalIds = criteria.externalId as List<String>
            if (externalIds.isNotEmpty())
                predicateSet.add(criteriaBuilder.and(vaultLinearStates.get<String>("externalId").`in`(externalIds)))
        }

        // deal participants
        criteria.participants?.let {
            val participants = criteria.participants as List<AbstractParty>
            val joinLinearStateToParty = vaultLinearStates.joinSet<VaultSchemaV1.VaultLinearStates, AbstractParty>("participants")
            predicateSet.add(criteriaBuilder.and(joinLinearStateToParty.`in`(participants)))
            criteriaQuery.distinct(true)
        }
        return predicateSet
    }

    override fun <L : PersistentState> parseCriteria(criteria: QueryCriteria.VaultCustomQueryCriteria<L>): Collection<Predicate> {
        log.trace { "Parsing VaultCustomQueryCriteria: $criteria" }

        val predicateSet = mutableSetOf<Predicate>()
        val entityStateClass = resolveEnclosingObjectFromExpression(criteria.expression)

        try {
            // ensure we re-use any existing instance of the same root entity
            val entityRoot =
                    rootEntities.getOrElse(entityStateClass) {
                        val entityRoot = criteriaQuery.from(entityStateClass)
                        rootEntities[entityStateClass] = entityRoot
                        entityRoot
                    }

            val joinPredicate = criteriaBuilder.equal(vaultStates.get<PersistentStateRef>("stateRef"), entityRoot.get<PersistentStateRef>("stateRef"))
            predicateSet.add(joinPredicate)

            // resolve general criteria expressions
            @Suppress("UNCHECKED_CAST")
            parseExpression(entityRoot as Root<L>, criteria.expression, predicateSet)
        } catch (e: Exception) {
            e.message?.let { message ->
                if (message.contains("Not an entity"))
                    throw VaultQueryException("""
                    Please register the entity '${entityStateClass.name}'
                    See https://docs.corda.net/api-persistence.html#custom-schema-registration for more information""")
            }
            throw VaultQueryException("Parsing error: ${e.message}")
        }
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
                    rootEntities.map { it.value }
                else
                    aggregateExpressions
        criteriaQuery.multiselect(selections)
        val combinedPredicates = commonPredicates.values.plus(predicateSet)
        criteriaQuery.where(*combinedPredicates.toTypedArray())

        return predicateSet
    }

    override fun parseCriteria(criteria: CommonQueryCriteria): Collection<Predicate> {
        log.trace { "Parsing CommonQueryCriteria: $criteria" }

        // state status
        stateTypes = criteria.status
        if (criteria.status != Vault.StateStatus.ALL) {
            val predicateID = Pair(VaultSchemaV1.VaultStates::stateStatus.name, EqualityComparisonOperator.EQUAL)
            if (commonPredicates.containsKey(predicateID)) {
                val existingStatus = ((commonPredicates[predicateID] as ComparisonPredicate).rightHandOperand as LiteralExpression).literal
                if (existingStatus != criteria.status) {
                    log.warn("Overriding previous attribute [${VaultSchemaV1.VaultStates::stateStatus.name}] value $existingStatus with ${criteria.status}")
                    commonPredicates.replace(predicateID, criteriaBuilder.equal(vaultStates.get<Vault.StateStatus>(VaultSchemaV1.VaultStates::stateStatus.name), criteria.status))
                }
            } else {
                commonPredicates.put(predicateID, criteriaBuilder.equal(vaultStates.get<Vault.StateStatus>(VaultSchemaV1.VaultStates::stateStatus.name), criteria.status))
            }
        }

        // contract state types
        val contractStateTypes = deriveContractStateTypes(criteria.contractStateTypes)
        if (contractStateTypes.isNotEmpty()) {
            val predicateID = Pair(VaultSchemaV1.VaultStates::contractStateClassName.name, CollectionOperator.IN)
            if (commonPredicates.containsKey(predicateID)) {
                val existingTypes = (commonPredicates[predicateID]!!.expressions[0] as InPredicate<*>).values.map { (it as LiteralExpression).literal }.toSet()
                if (existingTypes != contractStateTypes) {
                    log.warn("Enriching previous attribute [${VaultSchemaV1.VaultStates::contractStateClassName.name}] values [$existingTypes] with [$contractStateTypes]")
                    commonPredicates.replace(predicateID, criteriaBuilder.and(vaultStates.get<String>(VaultSchemaV1.VaultStates::contractStateClassName.name).`in`(contractStateTypes.plus(existingTypes))))
                }
            } else {
                commonPredicates.put(predicateID, criteriaBuilder.and(vaultStates.get<String>(VaultSchemaV1.VaultStates::contractStateClassName.name).`in`(contractStateTypes)))
            }
        }

        return emptySet()
    }

    private fun parse(sorting: Sort) {
        log.trace { "Parsing sorting specification: $sorting" }

        val orderCriteria = mutableListOf<Order>()

        sorting.columns.map { (sortAttribute, direction) ->
            val (entityStateClass, entityStateAttributeParent, entityStateAttributeChild) =
                    when (sortAttribute) {
                        is SortAttribute.Standard -> parse(sortAttribute.attribute)
                        is SortAttribute.Custom -> Triple(sortAttribute.entityStateClass, sortAttribute.entityStateColumnName, null)
                    }
            val sortEntityRoot =
                    rootEntities.getOrElse(entityStateClass) {
                        // scenario where sorting on attributes not parsed as criteria
                        val entityRoot = criteriaQuery.from(entityStateClass)
                        rootEntities[entityStateClass] = entityRoot
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
        }
    }

    private fun parse(sortAttribute: Sort.Attribute): Triple<Class<out PersistentState>, String, String?> {
        val entityClassAndColumnName: Triple<Class<out PersistentState>, String, String?> =
                when (sortAttribute) {
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