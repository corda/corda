/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

@file:JvmName("QueryCriteria")

package net.corda.core.node.services.vault

import net.corda.core.DoNotImplement
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.node.services.Vault
import net.corda.core.schemas.PersistentState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.OpaqueBytes
import java.time.Instant
import java.util.*
import javax.persistence.criteria.Predicate

interface GenericQueryCriteria<Q : GenericQueryCriteria<Q, *>, in P : BaseQueryCriteriaParser<Q, *, *>> {
    fun visit(parser: P): Collection<Predicate>

    interface ChainableQueryCriteria<Q : GenericQueryCriteria<Q, P>, in P : BaseQueryCriteriaParser<Q, P, *>> {

        interface AndVisitor<Q : GenericQueryCriteria<Q, P>, in P : BaseQueryCriteriaParser<Q, P, S>, in S : BaseSort> : GenericQueryCriteria<Q,P> {
            val a:Q
            val b:Q
            override fun visit(parser: P): Collection<Predicate> {
                return parser.parseAnd(this.a, this.b)
            }
        }

        interface OrVisitor<Q : GenericQueryCriteria<Q, P>, in P : BaseQueryCriteriaParser<Q, P, S>, in S : BaseSort> : GenericQueryCriteria<Q,P> {
            val a:Q
            val b:Q
            override fun visit(parser: P): Collection<Predicate> {
                return parser.parseOr(this.a, this.b)
            }
        }

        infix fun and(criteria: Q): Q
        infix fun or(criteria: Q): Q
    }
}

/**
 * Indexing assumptions:
 * QueryCriteria assumes underlying schema tables are correctly indexed for performance.
 */
@CordaSerializable
sealed class QueryCriteria : GenericQueryCriteria<QueryCriteria, IQueryCriteriaParser>, GenericQueryCriteria.ChainableQueryCriteria<QueryCriteria, IQueryCriteriaParser> {

    @CordaSerializable
    data class TimeCondition(val type: TimeInstantType, val predicate: ColumnPredicate<Instant>)

    /**
     * Select states based on their locks.
     *
     * @param [type] Whether to select all locked states, all unlocked states,
     *   specific locked states, or all unlocked states plus specific locked states.
     * @param [lockIds] The specific locked states to select (if applicable).
     */
    // DOCSTART VaultQuerySoftLockingCriteria
    @CordaSerializable
    data class SoftLockingCondition(val type: SoftLockingType, val lockIds: List<UUID> = emptyList())

    @CordaSerializable
    enum class SoftLockingType {
        UNLOCKED_ONLY,  // only unlocked states
        LOCKED_ONLY,    // only soft locked states
        SPECIFIED,      // only those soft locked states specified by lock id(s)
        UNLOCKED_AND_SPECIFIED   // all unlocked states plus those soft locked states specified by lock id(s)
    }
    // DOCEND VaultQuerySoftLockingCriteria

    abstract class CommonQueryCriteria : QueryCriteria() {
        abstract val status: Vault.StateStatus
        abstract val contractStateTypes: Set<Class<out ContractState>>?
        override fun visit(parser: IQueryCriteriaParser): Collection<Predicate> {
            return parser.parseCriteria(this)
        }
    }

    /**
     * VaultQueryCriteria: provides query by attributes defined in [VaultSchema.VaultStates]
     */
    data class VaultQueryCriteria @JvmOverloads constructor(override val status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
                                                            override val contractStateTypes: Set<Class<out ContractState>>? = null,
                                                            val stateRefs: List<StateRef>? = null,
                                                            val notary: List<AbstractParty>? = null,
                                                            val softLockingCondition: SoftLockingCondition? = null,
                                                            val timeCondition: TimeCondition? = null) : CommonQueryCriteria() {
        override fun visit(parser: IQueryCriteriaParser): Collection<Predicate> {
            super.visit(parser)
            return parser.parseCriteria(this)
        }
    }

    /**
     * LinearStateQueryCriteria: provides query by attributes defined in [VaultSchema.VaultLinearState]
     */
    data class LinearStateQueryCriteria @JvmOverloads constructor(val participants: List<AbstractParty>? = null,
                                                                  val uuid: List<UUID>? = null,
                                                                  val externalId: List<String>? = null,
                                                                  override val status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
                                                                  override val contractStateTypes: Set<Class<out ContractState>>? = null) : CommonQueryCriteria() {
        constructor(participants: List<AbstractParty>? = null,
                    linearId: List<UniqueIdentifier>? = null,
                    status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
                    contractStateTypes: Set<Class<out ContractState>>? = null) : this(participants, linearId?.map { it.id }, linearId?.mapNotNull { it.externalId }, status, contractStateTypes)

        override fun visit(parser: IQueryCriteriaParser): Collection<Predicate> {
            super.visit(parser)
            return parser.parseCriteria(this)
        }
    }

    /**
     * FungibleStateQueryCriteria: provides query by attributes defined in [VaultSchema.VaultFungibleState]
     *
     * Valid TokenType implementations defined by Amount<T> are
     *   [Currency] as used in [Cash] contract state
     *   [Commodity] as used in [CommodityContract] state
     */
    data class FungibleAssetQueryCriteria @JvmOverloads constructor(val participants: List<AbstractParty>? = null,
                                                                    val owner: List<AbstractParty>? = null,
                                                                    val quantity: ColumnPredicate<Long>? = null,
                                                                    val issuer: List<AbstractParty>? = null,
                                                                    val issuerRef: List<OpaqueBytes>? = null,
                                                                    override val status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
                                                                    override val contractStateTypes: Set<Class<out ContractState>>? = null) : CommonQueryCriteria() {
        override fun visit(parser: IQueryCriteriaParser): Collection<Predicate> {
            super.visit(parser)
            return parser.parseCriteria(this)
        }
    }

    /**
     * VaultCustomQueryCriteria: provides query by custom attributes defined in a contracts
     * [QueryableState] implementation.
     * (see Persistence documentation for more information)
     *
     * Params
     *  [expression] refers to a (composable) type safe [CriteriaExpression]
     *
     * Refer to [CommercialPaper.State] for a concrete example.
     */
    data class VaultCustomQueryCriteria<L : PersistentState> @JvmOverloads constructor
    (val expression: CriteriaExpression<L, Boolean>,
     override val status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
     override val contractStateTypes: Set<Class<out ContractState>>? = null) : CommonQueryCriteria() {
        override fun visit(parser: IQueryCriteriaParser): Collection<Predicate> {
            super.visit(parser)
            return parser.parseCriteria(this)
        }
    }

    // timestamps stored in the vault states table [VaultSchema.VaultStates]
    @CordaSerializable
    enum class TimeInstantType {
        RECORDED,
        CONSUMED
    }

    class AndComposition(override val a: QueryCriteria, override val b: QueryCriteria): QueryCriteria(), GenericQueryCriteria.ChainableQueryCriteria.AndVisitor<QueryCriteria, IQueryCriteriaParser, Sort>
    class OrComposition(override val a: QueryCriteria, override val b: QueryCriteria): QueryCriteria(), GenericQueryCriteria.ChainableQueryCriteria.OrVisitor<QueryCriteria, IQueryCriteriaParser, Sort>

    override fun and(criteria: QueryCriteria): QueryCriteria = AndComposition(this, criteria)
    override fun or(criteria: QueryCriteria): QueryCriteria = OrComposition(this, criteria)
}
@CordaSerializable
sealed class AttachmentQueryCriteria : GenericQueryCriteria<AttachmentQueryCriteria, AttachmentsQueryCriteriaParser>, GenericQueryCriteria.ChainableQueryCriteria<AttachmentQueryCriteria, AttachmentsQueryCriteriaParser> {
    /**
     * AttachmentsQueryCriteria:
     */
    data class AttachmentsQueryCriteria @JvmOverloads constructor (val uploaderCondition: ColumnPredicate<String>? = null,
                                                                   val filenameCondition: ColumnPredicate<String>? = null,
                                                                   val uploadDateCondition: ColumnPredicate<Instant>? = null) : AttachmentQueryCriteria() {
        override fun visit(parser: AttachmentsQueryCriteriaParser): Collection<Predicate> {
            return parser.parseCriteria(this)
        }
    }

    class AndComposition(override val a: AttachmentQueryCriteria, override val b: AttachmentQueryCriteria): AttachmentQueryCriteria(), GenericQueryCriteria.ChainableQueryCriteria.AndVisitor<AttachmentQueryCriteria, AttachmentsQueryCriteriaParser, AttachmentSort>
    class OrComposition(override val a: AttachmentQueryCriteria, override val b: AttachmentQueryCriteria): AttachmentQueryCriteria(), GenericQueryCriteria.ChainableQueryCriteria.OrVisitor<AttachmentQueryCriteria, AttachmentsQueryCriteriaParser, AttachmentSort>

    override fun and(criteria: AttachmentQueryCriteria): AttachmentQueryCriteria = AndComposition(this, criteria)
    override fun or(criteria: AttachmentQueryCriteria): AttachmentQueryCriteria = OrComposition(this, criteria)
}

interface BaseQueryCriteriaParser<Q: GenericQueryCriteria<Q, P>, in P: BaseQueryCriteriaParser<Q,P,S>, in S : BaseSort> {
    fun parseOr(left: Q, right: Q): Collection<Predicate>
    fun parseAnd(left: Q, right: Q): Collection<Predicate>
    fun parse(criteria: Q, sorting: S? = null): Collection<Predicate>
}

@DoNotImplement
interface IQueryCriteriaParser : BaseQueryCriteriaParser<QueryCriteria, IQueryCriteriaParser, Sort> {
    fun parseCriteria(criteria: QueryCriteria.CommonQueryCriteria): Collection<Predicate>
    fun parseCriteria(criteria: QueryCriteria.FungibleAssetQueryCriteria): Collection<Predicate>
    fun parseCriteria(criteria: QueryCriteria.LinearStateQueryCriteria): Collection<Predicate>
    fun <L : PersistentState> parseCriteria(criteria: QueryCriteria.VaultCustomQueryCriteria<L>): Collection<Predicate>
    fun parseCriteria(criteria: QueryCriteria.VaultQueryCriteria): Collection<Predicate>
}

interface AttachmentsQueryCriteriaParser : BaseQueryCriteriaParser<AttachmentQueryCriteria, AttachmentsQueryCriteriaParser, AttachmentSort>{
    fun parseCriteria(criteria: AttachmentQueryCriteria.AttachmentsQueryCriteria): Collection<Predicate>
}
