package net.corda.core.node.services.vault

import net.corda.core.contracts.Commodity
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria.AndComposition
import net.corda.core.node.services.vault.QueryCriteria.OrComposition
import net.corda.core.serialization.OpaqueBytes
import java.time.Instant
import java.util.*

/**
 * Indexing assumptions:
 * QueryCriteria assumes underlying schema tables are correctly indexed for performance.
 */

sealed class QueryCriteria {

    /**
     * VaultQueryCriteria: provides query by attributes defined in [VaultSchema.VaultStates]
     */
    data class VaultQueryCriteria @JvmOverloads constructor (
            val status: Vault.StateStatus = Vault.StateStatus.UNCONSUMED,
            val stateRefs: List<StateRef>? = null,
            val contractStateTypes: Set<Class<out ContractState>>? = null,
            val notaryName: List<String>? = null,
            val includeSoftlocks: Boolean? = true,
            val timeCondition: Logical<TimeInstantType, Array<Instant>>? = null,
            val participantIdentities: List<String>? = null) : QueryCriteria()

    /**
     * LinearStateQueryCriteria: provides query by attributes defined in [VaultSchema.VaultLinearState]
     */
    data class LinearStateQueryCriteria @JvmOverloads constructor(
            val linearId: List<UniqueIdentifier>? = null,
            val latestOnly: Boolean? = true,
            val dealRef: List<String>? = null,
            val dealPartyName: List<String>? = null) : QueryCriteria()

   /**
    * FungibleStateQueryCriteria: provides query by attributes defined in [VaultSchema.VaultFungibleState]
    *
    * Valid TokenType implementations defined by Amount<T> are
    *   [Currency] as used in [Cash] contract state
    *   [Commodity] as used in [CommodityContract] state
    */
    data class FungibleAssetQueryCriteria @JvmOverloads constructor(
            val ownerIdentity: List<String>? = null,
            val quantity: Logical<*,Long>? = null,
            val tokenType: List<Class<out Any>>? = null,
            val tokenValue: List<String>? = null,
            val issuerPartyName: List<String>? = null,
            val issuerRef: List<OpaqueBytes>? = null,
            val exitKeyIdentity: List<String>? = null) : QueryCriteria()

    /**
     * VaultIndexedQueryCriteria: provides query by custom attributes defined in a contracts
     * [QueryableState] implementation. A custom state must defined its own state attribute mappings
     * to a versioned generic Vault Index schema [PersistentGenericVaultIndexSchemaState]
     * (see Persistence documentation for more information)
     *
     * Refer to [CommercialPaper.State] for a concrete example.
     */
    data class VaultIndexedQueryCriteria<L,R>(val indexExpression: Logical<L,R>? = null) : QueryCriteria()

    /**
     * Specify any query criteria by leveraging the Requery Query DSL:
     * provides query ability on any [Queryable] custom contract state attribute defined by a [MappedSchema]
     */
    data class VaultCustomQueryCriteria<L,R>(val expression: Logical<L,R>? = null) : QueryCriteria()

    // enable composition of [QueryCriteria]
    data class AndComposition(val a: QueryCriteria, val b: QueryCriteria): QueryCriteria()
    data class OrComposition(val a: QueryCriteria, val b: QueryCriteria): QueryCriteria()

    // timestamps stored in the vault states table [VaultSchema.VaultStates]
    enum class TimeInstantType {
        RECORDED,
        CONSUMED
    }
}

infix fun QueryCriteria.and(criteria: QueryCriteria): QueryCriteria = AndComposition(this, criteria)
infix fun QueryCriteria.or(criteria: QueryCriteria): QueryCriteria = OrComposition(this, criteria)
