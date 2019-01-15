@file:JvmName("QueryCriteria")

package net.corda.core.node.services.vault

import net.corda.core.DoNotImplement
import net.corda.core.contracts.ContractClassName
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.internal.cordapp.CordappResolver
import net.corda.core.node.services.Vault
import net.corda.core.schemas.StatePersistable
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.OpaqueBytes
import java.security.PublicKey
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
        open val relevancyStatus: Vault.RelevancyStatus = Vault.RelevancyStatus.RELEVANT
            get() = if (CordappResolver.currentTargetVersion < 4) Vault.RelevancyStatus.ALL else field
        open val constraintTypes: Set<Vault.ConstraintInfo.Type> = emptySet()
        open val constraints: Set<Vault.ConstraintInfo> = emptySet()
        open val participants: List<AbstractParty>? = null
        abstract val contractStateTypes: Set<Class<out ContractState>>?
        override fun visit(parser: IQueryCriteriaParser): Collection<Predicate> {
            return parser.parseCriteria(this)
        }
    }

    /**
     * VaultQueryCriteria: provides query by attributes defined in [VaultSchema.VaultStates]
     */
    data class VaultQueryCriteria @JvmOverloads constructor(
            override val status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
            override val contractStateTypes: Set<Class<out ContractState>>? = null,
            val stateRefs: List<StateRef>? = null,
            val notary: List<AbstractParty>? = null,
            val softLockingCondition: SoftLockingCondition? = null,
            val timeCondition: TimeCondition? = null
    ) : CommonQueryCriteria() {
        // These extra fields are handled this way to preserve Kotlin wire compatibility wrt additional parameters with default values.
        constructor(
                status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
                contractStateTypes: Set<Class<out ContractState>>? = null,
                stateRefs: List<StateRef>? = null,
                notary: List<AbstractParty>? = null,
                softLockingCondition: SoftLockingCondition? = null,
                timeCondition: TimeCondition? = null,
                relevancyStatus: Vault.RelevancyStatus = Vault.RelevancyStatus.RELEVANT,
                constraintTypes: Set<Vault.ConstraintInfo.Type> = emptySet(),
                constraints: Set<Vault.ConstraintInfo> = emptySet(),
                participants: List<AbstractParty>? = null
        ) : this(status, contractStateTypes, stateRefs, notary, softLockingCondition, timeCondition) {
            this.relevancyStatus = relevancyStatus
            this.constraintTypes = constraintTypes
            this.constraints = constraints
            this.participants = participants
        }

        override var relevancyStatus: Vault.RelevancyStatus = Vault.RelevancyStatus.RELEVANT
            get() = if (CordappResolver.currentTargetVersion < 4) Vault.RelevancyStatus.ALL else field
            private set

        override var constraintTypes: Set<Vault.ConstraintInfo.Type> = emptySet()
            private set

        override var constraints: Set<Vault.ConstraintInfo> = emptySet()
            private set

        override var participants: List<AbstractParty>? = null
            private set

        override fun visit(parser: IQueryCriteriaParser): Collection<Predicate> {
            super.visit(parser)
            return parser.parseCriteria(this)
        }

        fun copy(
                status: Vault.StateStatus = this.status,
                contractStateTypes: Set<Class<out ContractState>>? = this.contractStateTypes,
                stateRefs: List<StateRef>? = this.stateRefs,
                notary: List<AbstractParty>? = this.notary,
                softLockingCondition: SoftLockingCondition? = this.softLockingCondition,
                timeCondition: TimeCondition? = this.timeCondition,
                relevancyStatus: Vault.RelevancyStatus = this.relevancyStatus,
                constraintTypes: Set<Vault.ConstraintInfo.Type> = this.constraintTypes,
                constraints: Set<Vault.ConstraintInfo> = this.constraints,
                participants: List<AbstractParty>? = this.participants
        ): VaultQueryCriteria {
            return VaultQueryCriteria(
                    status,
                    contractStateTypes,
                    stateRefs,
                    notary,
                    softLockingCondition,
                    timeCondition,
                    relevancyStatus,
                    constraintTypes,
                    constraints,
                    participants
            )
        }
    }

    /**
     * LinearStateQueryCriteria: provides query by attributes defined in [VaultSchema.VaultLinearState]
     */
    data class LinearStateQueryCriteria(
            override val participants: List<AbstractParty>?,
            val uuid: List<UUID>? = null,
            val externalId: List<String>? = null,
            override val status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
            override val contractStateTypes: Set<Class<out ContractState>>? = null,
            override val relevancyStatus: Vault.RelevancyStatus = if (CordappResolver.currentTargetVersion < 4) Vault.RelevancyStatus.ALL else Vault.RelevancyStatus.RELEVANT
    ) : CommonQueryCriteria() {
        // V3 c'tor
        @JvmOverloads
        constructor(
                participants: List<AbstractParty>? = null,
                uuid: List<UUID>? = null,
                externalId: List<String>? = null,
                status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
                contractStateTypes: Set<Class<out ContractState>>? = null
        ) : this(participants, uuid, externalId, status, contractStateTypes, Vault.RelevancyStatus.ALL)

        constructor(
                participants: List<AbstractParty>? = null,
                linearId: List<UniqueIdentifier>? = null,
                status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
                contractStateTypes: Set<Class<out ContractState>>? = null,
                relevancyStatus: Vault.RelevancyStatus = Vault.RelevancyStatus.ALL
        ) : this(participants, linearId?.map { it.id }, linearId?.mapNotNull { it.externalId }, status, contractStateTypes, relevancyStatus)

        // V3 c'tor
        constructor(
                participants: List<AbstractParty>? = null,
                linearId: List<UniqueIdentifier>? = null,
                status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
                contractStateTypes: Set<Class<out ContractState>>? = null
        ) : this(participants, linearId, status, contractStateTypes, Vault.RelevancyStatus.ALL)

        override fun visit(parser: IQueryCriteriaParser): Collection<Predicate> {
            super.visit(parser)
            return parser.parseCriteria(this)
        }

        fun copy(
                participants: List<AbstractParty>? = this.participants,
                uuid: List<UUID>? = this.uuid,
                externalId: List<String>? = this.externalId,
                status: Vault.StateStatus = this.status,
                contractStateTypes: Set<Class<out ContractState>>? = this.contractStateTypes
        ): LinearStateQueryCriteria {
            return LinearStateQueryCriteria(
                    participants,
                    uuid,
                    externalId,
                    status,
                    contractStateTypes,
                    relevancyStatus
            )
        }
    }

    /**
     * FungibleStateQueryCriteria: provides query by attributes defined in [VaultSchema.VaultFungibleStates]
     */
    data class FungibleStateQueryCriteria(
            override val participants: List<AbstractParty>? = null,
            val quantity: ColumnPredicate<Long>? = null,
            override val status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
            override val contractStateTypes: Set<Class<out ContractState>>? = null,
            override val relevancyStatus: Vault.RelevancyStatus = if (CordappResolver.currentTargetVersion < 4) Vault.RelevancyStatus.ALL else Vault.RelevancyStatus.RELEVANT
    ) : CommonQueryCriteria() {
        override fun visit(parser: IQueryCriteriaParser): Collection<Predicate> {
            super.visit(parser)
            return parser.parseCriteria(this)
        }
    }

    /**
     * FungibleStateQueryCriteria: provides query by attributes defined in [VaultSchema.VaultFungibleStates]
     */
    data class FungibleAssetQueryCriteria constructor(
            override val participants: List<AbstractParty>? = null,
            val owner: List<AbstractParty>? = null,
            val quantity: ColumnPredicate<Long>? = null,
            val issuer: List<AbstractParty>? = null,
            val issuerRef: List<OpaqueBytes>? = null,
            override val status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
            override val contractStateTypes: Set<Class<out ContractState>>? = null,
            override val relevancyStatus: Vault.RelevancyStatus = if (CordappResolver.currentTargetVersion < 4) Vault.RelevancyStatus.ALL else Vault.RelevancyStatus.RELEVANT
    ) : CommonQueryCriteria() {
        override fun visit(parser: IQueryCriteriaParser): Collection<Predicate> {
            super.visit(parser)
            return parser.parseCriteria(this)
        }

        fun copy(
                participants: List<AbstractParty>? = this.participants,
                owner: List<AbstractParty>? = this.owner,
                quantity: ColumnPredicate<Long>? = this.quantity,
                issuer: List<AbstractParty>? = this.issuer,
                issuerRef: List<OpaqueBytes>? = this.issuerRef,
                status: Vault.StateStatus = this.status,
                contractStateTypes: Set<Class<out ContractState>>? = this.contractStateTypes
        ): FungibleAssetQueryCriteria {
            return FungibleAssetQueryCriteria(
                    participants,
                    owner,
                    quantity,
                    issuer,
                    issuerRef,
                    status,
                    contractStateTypes,
                    relevancyStatus
            )
        }
    }

    /**
     * VaultCustomQueryCriteria: provides query by custom attributes defined in a contracts
     * [QueryableState] implementation.
     * (see Persistence documentation for more information)
     *
     * Params
     *  [expression] refers to a (composable) type safe [CriteriaExpression]
     */
    data class VaultCustomQueryCriteria<L : StatePersistable> constructor(
            val expression: CriteriaExpression<L, Boolean>,
            override val status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
            override val contractStateTypes: Set<Class<out ContractState>>? = null,
            override val relevancyStatus: Vault.RelevancyStatus = if (CordappResolver.currentTargetVersion < 4) Vault.RelevancyStatus.ALL else Vault.RelevancyStatus.RELEVANT
    ) : CommonQueryCriteria() {
        @JvmOverloads constructor(
                expression: CriteriaExpression<L, Boolean>,
                status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
                contractStateTypes: Set<Class<out ContractState>>? = null
        ) : this(expression, status, contractStateTypes, Vault.RelevancyStatus.RELEVANT)

        override fun visit(parser: IQueryCriteriaParser): Collection<Predicate> {
            super.visit(parser)
            return parser.parseCriteria(this)
        }

        fun copy(
                expression: CriteriaExpression<L, Boolean> = this.expression,
                status: Vault.StateStatus = this.status,
                contractStateTypes: Set<Class<out ContractState>>? = this.contractStateTypes
        ): VaultCustomQueryCriteria<L> {
            return VaultCustomQueryCriteria(
                    expression,
                    status,
                    contractStateTypes,
                    relevancyStatus
            )
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
    data class AttachmentsQueryCriteria(val uploaderCondition: ColumnPredicate<String>? = null,
                                        val filenameCondition: ColumnPredicate<String>? = null,
                                        val uploadDateCondition: ColumnPredicate<Instant>? = null,
                                        val contractClassNamesCondition: ColumnPredicate<List<ContractClassName>>? = null,
                                        val signersCondition: ColumnPredicate<List<PublicKey>>? = null,
                                        val isSignedCondition: ColumnPredicate<Boolean>? = null,
                                        val versionCondition: ColumnPredicate<Int>? = null) : AttachmentQueryCriteria() {
        // V3 c'tors
        constructor(uploaderCondition: ColumnPredicate<String>? = null,
                    filenameCondition: ColumnPredicate<String>? = null,
                    uploadDateCondition: ColumnPredicate<Instant>? = null) : this(uploaderCondition, filenameCondition, uploadDateCondition, null)
        constructor(uploaderCondition: ColumnPredicate<String>?) : this(uploaderCondition, null)
        constructor(uploaderCondition: ColumnPredicate<String>?, filenameCondition: ColumnPredicate<String>?) : this(uploaderCondition, filenameCondition, null)

        override fun visit(parser: AttachmentsQueryCriteriaParser): Collection<Predicate> {
            return parser.parseCriteria(this)
        }

        fun copy(
                uploaderCondition: ColumnPredicate<String>? = this.uploaderCondition,
                filenameCondition: ColumnPredicate<String>? = this.filenameCondition,
                uploadDateCondition: ColumnPredicate<Instant>? = this.uploadDateCondition
        ): AttachmentsQueryCriteria {
            return AttachmentsQueryCriteria(
                    uploaderCondition,
                    filenameCondition,
                    uploadDateCondition,
                    contractClassNamesCondition,
                    signersCondition,
                    isSignedCondition,
                    versionCondition
            )
        }

        fun withUploader(uploaderPredicate: ColumnPredicate<String>) = copy(uploaderCondition = uploaderPredicate)
        fun withFilename(filenamePredicate: ColumnPredicate<String>) = copy(filenameCondition = filenamePredicate)
        fun withUploadDate(uploadDatePredicate: ColumnPredicate<Instant>) = copy(uploadDateCondition = uploadDatePredicate)
        fun withContractClassNames(contractClassNamesPredicate: ColumnPredicate<List<ContractClassName>>) = copy(contractClassNamesCondition = contractClassNamesPredicate)
        fun withSigners(signersPredicate: ColumnPredicate<List<PublicKey>>) = copy(signersCondition = signersPredicate)
        fun isSigned(isSignedPredicate: ColumnPredicate<Boolean>) = copy(isSignedCondition = isSignedPredicate)
        fun withVersion(versionPredicate: ColumnPredicate<Int>) = copy(versionCondition = versionPredicate)
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
    fun <L : StatePersistable> parseCriteria(criteria: QueryCriteria.VaultCustomQueryCriteria<L>): Collection<Predicate>
    fun parseCriteria(criteria: QueryCriteria.VaultQueryCriteria): Collection<Predicate>
}

interface AttachmentsQueryCriteriaParser : BaseQueryCriteriaParser<AttachmentQueryCriteria, AttachmentsQueryCriteriaParser, AttachmentSort>{
    fun parseCriteria(criteria: AttachmentQueryCriteria.AttachmentsQueryCriteria): Collection<Predicate>
}
