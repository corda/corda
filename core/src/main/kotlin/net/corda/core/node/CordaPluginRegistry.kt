package net.corda.core.node

import net.corda.core.contracts.ContractState
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.services.VaultQueryService
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.SerializationCustomization
import java.util.function.Function

/**
 * Implement this interface on a class advertised in a META-INF/services/net.corda.core.node.CordaPluginRegistry file
 * to extend a Corda node with additional application services.
 */
abstract class CordaPluginRegistry {
    /**
     * Optionally whitelist types for use in object serialization, as we lock down the types that can be serialized.
     *
     * For example, if you add a new [net.corda.core.contracts.ContractState] it needs to be whitelisted.  You can do that
     * either by adding the [net.corda.core.serialization.CordaSerializable] annotation or via this method.
     **
     * @return true if you register types, otherwise you will be filtered out of the list of plugins considered in future.
     */
    open fun customizeSerialization(custom: SerializationCustomization): Boolean = false

    /**
     * Optionally, custom schemas to be used for contract state persistence and vault custom querying
     *
     * For example, if you implement the [QueryableState] interface on a new [ContractState]
     * it needs to be registered here if you wish to perform custom queries on schema entity attributes using the
     * [VaultQueryService] API
     */
    open val requiredSchemas: Set<MappedSchema> get() = emptySet()
}