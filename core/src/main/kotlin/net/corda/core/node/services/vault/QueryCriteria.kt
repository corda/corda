@file:JvmName("QueryCriteria")

package net.corda.core.node.services.vault

import net.corda.core.DoNotImplement
import net.corda.core.contracts.ContractClassName
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.node.services.Vault
import net.corda.core.schemas.StatePersistable
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.DeprecatedConstructorForDeserialization
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
        open val relevancyStatus: Vault.RelevancyStatus = Vault.RelevancyStatus.ALL
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
    data class VaultQueryCriteria(
            override val status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
            override val contractStateTypes: Set<Class<out ContractState>>? = null,
            val stateRefs: List<StateRef>? = null,
            val notary: List<AbstractParty>? = null,
            val softLockingCondition: SoftLockingCondition? = null,
            val timeCondition: TimeCondition? = null,
            override val relevancyStatus: Vault.RelevancyStatus = Vault.RelevancyStatus.ALL,
            override val constraintTypes: Set<Vault.ConstraintInfo.Type> = emptySet(),
            override val constraints: Set<Vault.ConstraintInfo> = emptySet(),
            override val participants: List<AbstractParty>? = null
    ) : CommonQueryCriteria() {
        // V3 c'tors
        // These have to be manually specified as @JvmOverloads for some reason causes declaration clashes
        @DeprecatedConstructorForDeserialization(version = 6)
        constructor(
                status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
                contractStateTypes: Set<Class<out ContractState>>? = null,
                stateRefs: List<StateRef>? = null,
                notary: List<AbstractParty>? = null,
                softLockingCondition: SoftLockingCondition? = null,
                timeCondition: TimeCondition? = null
        ) : this(status, contractStateTypes, stateRefs, notary, softLockingCondition, timeCondition, participants = null)
        @DeprecatedConstructorForDeserialization(version = 1)
        constructor(status: Vault.StateStatus) : this(status, participants = null)
        @DeprecatedConstructorForDeserialization(version = 2)
        constructor(status: Vault.StateStatus, contractStateTypes: Set<Class<out ContractState>>?) : this(status, contractStateTypes, participants = null)
        @DeprecatedConstructorForDeserialization(version = 3)
        constructor(status: Vault.StateStatus, contractStateTypes: Set<Class<out ContractState>>?, stateRefs: List<StateRef>?) : this(
                status, contractStateTypes, stateRefs, participants = null
        )
        @DeprecatedConstructorForDeserialization(version = 4)
        constructor(status: Vault.StateStatus, contractStateTypes: Set<Class<out ContractState>>?, stateRefs: List<StateRef>?, notary: List<AbstractParty>?) : this(
                status, contractStateTypes, stateRefs, notary, participants = null
        )
        @DeprecatedConstructorForDeserialization(version = 5)
        constructor(status: Vault.StateStatus, contractStateTypes: Set<Class<out ContractState>>?, stateRefs: List<StateRef>?, notary: List<AbstractParty>?, softLockingCondition: SoftLockingCondition?) : this(
                status, contractStateTypes, stateRefs, notary, softLockingCondition, participants = null
        )

        override fun visit(parser: IQueryCriteriaParser): Collection<Predicate> {
            super.visit(parser)
            return parser.parseCriteria(this)
        }

        fun withStatus(status: Vault.StateStatus): VaultQueryCriteria = copy(status = status)
        fun withContractStateTypes(contractStateTypes: Set<Class<out ContractState>>): VaultQueryCriteria = copy(contractStateTypes = contractStateTypes)
        fun withStateRefs(stateRefs: List<StateRef>): VaultQueryCriteria = copy(stateRefs = stateRefs)
        fun withNotary(notary: List<AbstractParty>): VaultQueryCriteria = copy(notary = notary)
        fun withSoftLockingCondition(softLockingCondition: SoftLockingCondition): VaultQueryCriteria = copy(softLockingCondition = softLockingCondition)
        fun withTimeCondition(timeCondition: TimeCondition): VaultQueryCriteria = copy(timeCondition = timeCondition)
        fun withRelevancyStatus(relevancyStatus: Vault.RelevancyStatus): VaultQueryCriteria = copy(relevancyStatus = relevancyStatus)
        fun withConstraintTypes(constraintTypes: Set<Vault.ConstraintInfo.Type>): VaultQueryCriteria = copy(constraintTypes = constraintTypes)
        fun withConstraints(constraints: Set<Vault.ConstraintInfo>): VaultQueryCriteria = copy(constraints = constraints)
        fun withParticipants(participants: List<AbstractParty>): VaultQueryCriteria = copy(participants = participants)

        fun copy(
                status: Vault.StateStatus = this.status,
                contractStateTypes: Set<Class<out ContractState>>? = this.contractStateTypes,
                stateRefs: List<StateRef>? = this.stateRefs,
                notary: List<AbstractParty>? = this.notary,
                softLockingCondition: SoftLockingCondition? = this.softLockingCondition,
                timeCondition: TimeCondition? = this.timeCondition
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
            override val relevancyStatus: Vault.RelevancyStatus = Vault.RelevancyStatus.ALL
    ) : CommonQueryCriteria() {
        // V3 c'tor
        @JvmOverloads
        @DeprecatedConstructorForDeserialization(version = 2)
        constructor(
                participants: List<AbstractParty>? = null,
                uuid: List<UUID>? = null,
                externalId: List<String>? = null,
                status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
                contractStateTypes: Set<Class<out ContractState>>? = null
        ) : this(participants, uuid, externalId, status, contractStateTypes, Vault.RelevancyStatus.ALL)

        @DeprecatedConstructorForDeserialization(version = 3)
        constructor(
                participants: List<AbstractParty>? = null,
                linearId: List<UniqueIdentifier>? = null,
                status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
                contractStateTypes: Set<Class<out ContractState>>? = null,
                relevancyStatus: Vault.RelevancyStatus = Vault.RelevancyStatus.ALL
        ) : this(participants, linearId?.map { it.id }, linearId?.mapNotNull { it.externalId }, status, contractStateTypes, relevancyStatus)

        // V3 c'tor
        @DeprecatedConstructorForDeserialization(version = 1)
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

        fun withParticipants(participants: List<AbstractParty>): LinearStateQueryCriteria = copy(participants = participants)
        fun withUuid(uuid: List<UUID>): LinearStateQueryCriteria = copy(uuid = uuid)
        fun withExternalId(externalId: List<String>): LinearStateQueryCriteria = copy(externalId = externalId)
        fun withStatus(status: Vault.StateStatus): LinearStateQueryCriteria = copy(status = status)
        fun withContractStateTypes(contractStateTypes: Set<Class<out ContractState>>): LinearStateQueryCriteria = copy(contractStateTypes = contractStateTypes)
        fun withRelevancyStatus(relevancyStatus: Vault.RelevancyStatus): LinearStateQueryCriteria = copy(relevancyStatus = relevancyStatus)

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
            override val relevancyStatus: Vault.RelevancyStatus = Vault.RelevancyStatus.ALL
    ) : CommonQueryCriteria() {
        override fun visit(parser: IQueryCriteriaParser): Collection<Predicate> {
            super.visit(parser)
            return parser.parseCriteria(this)
        }

        fun withParticipants(participants: List<AbstractParty>): FungibleStateQueryCriteria = copy(participants = participants)
        fun withQuantity(quantity: ColumnPredicate<Long>): FungibleStateQueryCriteria = copy(quantity = quantity)
        fun withStatus(status: Vault.StateStatus): FungibleStateQueryCriteria = copy(status = status)
        fun withContractStateTypes(contractStateTypes: Set<Class<out ContractState>>): FungibleStateQueryCriteria = copy(contractStateTypes = contractStateTypes)
        fun withRelevancyStatus(relevancyStatus: Vault.RelevancyStatus): FungibleStateQueryCriteria = copy(relevancyStatus = relevancyStatus)
    }

    /**
     * FungibleStateQueryCriteria: provides query by attributes defined in [VaultSchema.VaultFungibleStates]
     */
    data class FungibleAssetQueryCriteria(
            override val participants: List<AbstractParty>? = null,
            val owner: List<AbstractParty>? = null,
            val quantity: ColumnPredicate<Long>? = null,
            val issuer: List<AbstractParty>? = null,
            val issuerRef: List<OpaqueBytes>? = null,
            override val status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
            override val contractStateTypes: Set<Class<out ContractState>>? = null,
            override val relevancyStatus: Vault.RelevancyStatus
    ) : CommonQueryCriteria() {
        @JvmOverloads
        @DeprecatedConstructorForDeserialization(version = 1)
        constructor(
                participants: List<AbstractParty>? = null,
                owner: List<AbstractParty>? = null,
                quantity: ColumnPredicate<Long>? = null,
                issuer: List<AbstractParty>? = null,
                issuerRef: List<OpaqueBytes>? = null,
                status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
                contractStateTypes: Set<Class<out ContractState>>? = null
        ) : this(participants, owner, quantity, issuer, issuerRef, status, contractStateTypes, Vault.RelevancyStatus.ALL)

        override fun visit(parser: IQueryCriteriaParser): Collection<Predicate> {
            super.visit(parser)
            return parser.parseCriteria(this)
        }

        fun withParticipants(participants: List<AbstractParty>): FungibleAssetQueryCriteria = copy(participants = participants)
        fun withOwner(owner: List<AbstractParty>): FungibleAssetQueryCriteria = copy(owner = owner)
        fun withQuantity(quantity: ColumnPredicate<Long>): FungibleAssetQueryCriteria = copy(quantity = quantity)
        fun withIssuer(issuer: List<AbstractParty>): FungibleAssetQueryCriteria = copy(issuer = issuer)
        fun withissuerRef(issuerRef: List<OpaqueBytes>): FungibleAssetQueryCriteria = copy(issuerRef = issuerRef)
        fun withStatus(status: Vault.StateStatus): FungibleAssetQueryCriteria = copy(status = status)
        fun withContractStateTypes(contractStateTypes: Set<Class<out ContractState>>): FungibleAssetQueryCriteria = copy(contractStateTypes = contractStateTypes)
        fun withRelevancyStatus(relevancyStatus: Vault.RelevancyStatus): FungibleAssetQueryCriteria = copy(relevancyStatus = relevancyStatus)

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
    data class VaultCustomQueryCriteria<L : StatePersistable>(
            val expression: CriteriaExpression<L, Boolean>,
            override val status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
            override val contractStateTypes: Set<Class<out ContractState>>? = null,
            override val relevancyStatus: Vault.RelevancyStatus = Vault.RelevancyStatus.ALL
    ) : CommonQueryCriteria() {
        @JvmOverloads
        @DeprecatedConstructorForDeserialization(version = 1)
        constructor(
                expression: CriteriaExpression<L, Boolean>,
                status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
                contractStateTypes: Set<Class<out ContractState>>? = null
        ) : this(expression, status, contractStateTypes, Vault.RelevancyStatus.ALL)

        override fun visit(parser: IQueryCriteriaParser): Collection<Predicate> {
            super.visit(parser)
            return parser.parseCriteria(this)
        }

        fun withExpression(expression: CriteriaExpression<L, Boolean>): VaultCustomQueryCriteria<L> = copy(expression = expression)
        fun withStatus(status: Vault.StateStatus): VaultCustomQueryCriteria<L> = copy(status = status)
        fun withContractStateTypes(contractStateTypes: Set<Class<out ContractState>>): VaultCustomQueryCriteria<L> = copy(contractStateTypes = contractStateTypes)
        fun withRelevancyStatus(relevancyStatus: Vault.RelevancyStatus): VaultCustomQueryCriteria<L> = copy(relevancyStatus = relevancyStatus)

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
        @DeprecatedConstructorForDeserialization(version = 3)
        constructor(uploaderCondition: ColumnPredicate<String>? = null,
                    filenameCondition: ColumnPredicate<String>? = null,
                    uploadDateCondition: ColumnPredicate<Instant>? = null) : this(uploaderCondition, filenameCondition, uploadDateCondition, null)
        @DeprecatedConstructorForDeserialization(version = 1)
        constructor(uploaderCondition: ColumnPredicate<String>?) : this(uploaderCondition, null)
        @DeprecatedConstructorForDeserialization(version = 2)
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

        fun withUploader(uploaderPredicate: ColumnPredicate<String>): AttachmentsQueryCriteria = copy(uploaderCondition = uploaderPredicate)
        fun withFilename(filenamePredicate: ColumnPredicate<String>): AttachmentsQueryCriteria = copy(filenameCondition = filenamePredicate)
        fun withUploadDate(uploadDatePredicate: ColumnPredicate<Instant>): AttachmentsQueryCriteria = copy(uploadDateCondition = uploadDatePredicate)
        fun withContractClassNames(contractClassNamesPredicate: ColumnPredicate<List<ContractClassName>>): AttachmentsQueryCriteria = copy(contractClassNamesCondition = contractClassNamesPredicate)
        fun withSigners(signersPredicate: ColumnPredicate<List<PublicKey>>): AttachmentsQueryCriteria = copy(signersCondition = signersPredicate)
        fun isSigned(isSignedPredicate: ColumnPredicate<Boolean>): AttachmentsQueryCriteria = copy(isSignedCondition = isSignedPredicate)
        fun withVersion(versionPredicate: ColumnPredicate<Int>): AttachmentsQueryCriteria = copy(versionCondition = versionPredicate)
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
