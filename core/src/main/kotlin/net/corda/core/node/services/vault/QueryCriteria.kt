@file:JvmName("QueryCriteria")

package net.corda.core.node.services.vault

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.identity.AbstractParty
import net.corda.core.node.services.Vault
import net.corda.core.schemas.PersistentState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.OpaqueBytes
import org.bouncycastle.asn1.x500.X500Name
import java.time.Instant
import java.util.*
import javax.persistence.criteria.Predicate

/**
 * Indexing assumptions:
 * QueryCriteria assumes underlying schema tables are correctly indexed for performance.
 */
@CordaSerializable
sealed class QueryCriteria {
    abstract fun visit(parser: IQueryCriteriaParser): Collection<Predicate>

    @CordaSerializable
    data class TimeCondition(val type: TimeInstantType, val predicate: ColumnPredicate<Instant>)

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
        override fun visit(parser: IQueryCriteriaParser): Collection<Predicate> {
            return parser.parseCriteria(this)
        }
    }

    /**
     * VaultQueryCriteria: provides query by attributes defined in [VaultSchema.VaultStates]
     */
    data class VaultQueryCriteria @JvmOverloads constructor (override val status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
                                                             val contractStateTypes: Set<Class<out ContractState>>? = null,
                                                             val stateRefs: List<StateRef>? = null,
                                                             val notary: List<AbstractParty>? = null,
                                                             val softLockingCondition: SoftLockingCondition? = null,
                                                             val timeCondition: TimeCondition? = null) : CommonQueryCriteria() {
        override fun visit(parser: IQueryCriteriaParser): Collection<Predicate> {
            return parser.parseCriteria(this as CommonQueryCriteria).plus(parser.parseCriteria(this))
        }
    }

    /**
     * LinearStateQueryCriteria: provides query by attributes defined in [VaultSchema.VaultLinearState]
     */
    data class LinearStateQueryCriteria @JvmOverloads constructor(val participants: List<AbstractParty>? = null,
                                                                  val uuid: List<UUID>? = null,
                                                                  val externalId: List<String>? = null,
                                                                  override val status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED) : CommonQueryCriteria() {
        override fun visit(parser: IQueryCriteriaParser): Collection<Predicate> {
            return parser.parseCriteria(this as CommonQueryCriteria).plus(parser.parseCriteria(this))
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
                                                                    override val status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED) : CommonQueryCriteria() {
       override fun visit(parser: IQueryCriteriaParser): Collection<Predicate> {
           return parser.parseCriteria(this as CommonQueryCriteria).plus(parser.parseCriteria(this))
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
                                     override val status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED) : CommonQueryCriteria() {
        override fun visit(parser: IQueryCriteriaParser): Collection<Predicate> {
            return parser.parseCriteria(this as CommonQueryCriteria).plus(parser.parseCriteria(this))
        }
    }

    // enable composition of [QueryCriteria]
    private data class AndComposition(val a: QueryCriteria, val b: QueryCriteria): QueryCriteria() {
        override fun visit(parser: IQueryCriteriaParser): Collection<Predicate> {
            return parser.parseAnd(this.a, this.b)
        }
    }

    private data class OrComposition(val a: QueryCriteria, val b: QueryCriteria): QueryCriteria() {
        override fun visit(parser: IQueryCriteriaParser): Collection<Predicate> {
            return parser.parseOr(this.a, this.b)
        }
    }

    // timestamps stored in the vault states table [VaultSchema.VaultStates]
    @CordaSerializable
    enum class TimeInstantType {
        RECORDED,
        CONSUMED
    }

    infix fun and(criteria: QueryCriteria): QueryCriteria = AndComposition(this, criteria)
    infix fun or(criteria: QueryCriteria): QueryCriteria = OrComposition(this, criteria)
}

interface IQueryCriteriaParser {
    fun parseCriteria(criteria: QueryCriteria.CommonQueryCriteria): Collection<Predicate>
    fun parseCriteria(criteria: QueryCriteria.FungibleAssetQueryCriteria): Collection<Predicate>
    fun parseCriteria(criteria: QueryCriteria.LinearStateQueryCriteria): Collection<Predicate>
    fun <L: PersistentState> parseCriteria(criteria: QueryCriteria.VaultCustomQueryCriteria<L>): Collection<Predicate>
    fun parseCriteria(criteria: QueryCriteria.VaultQueryCriteria): Collection<Predicate>
    fun parseOr(left: QueryCriteria, right: QueryCriteria): Collection<Predicate>
    fun parseAnd(left: QueryCriteria, right: QueryCriteria): Collection<Predicate>
    fun parse(criteria: QueryCriteria, sorting: Sort? = null) : Collection<Predicate>
}
