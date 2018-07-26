package net.corda.groups.utilities

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateAndRef
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.groups.contracts.Group
import net.corda.groups.schemas.GroupSchemaV1
import java.security.PublicKey

@Suspendable
fun getGroupByKey(key: PublicKey, services: ServiceHub): StateAndRef<Group.State>? {
    val query = builder {
        val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        val customCriteria = QueryCriteria.VaultCustomQueryCriteria(GroupSchemaV1.PersistentGroupState::key.equal(key.encoded))
        generalCriteria.and(customCriteria)
    }
    return services.vaultService.queryBy<Group.State>(query).states.singleOrNull()
}