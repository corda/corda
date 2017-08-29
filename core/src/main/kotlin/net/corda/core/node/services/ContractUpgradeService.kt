package net.corda.core.node.services

import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UpgradedContract

/**
 * The [ContractUpgradeService] is responsible for securely upgrading contract state objects according to
 * a specified and mutually agreed (amongst participants) contract version.
 * See also [ContractUpgradeFlow] to understand the workflow associated with contract upgrades.
 */
interface ContractUpgradeService {

    /** Get contracts we would be willing to upgrade the suggested contract to. */
    fun getAuthorisedContractUpgrade(ref: StateRef): Class<out UpgradedContract<*, *>>?

    /**
     * Authorise a contract state upgrade.
     * This will store the upgrade authorisation in the vault, and will be queried by [ContractUpgradeFlow.Acceptor] during contract upgrade process.
     * Invoking this method indicate the node is willing to upgrade the [state] using the [upgradedContractClass].
     * This method will NOT initiate the upgrade process. To start the upgrade process, see [ContractUpgradeFlow.Instigator].
     */
    fun authoriseContractUpgrade(stateAndRef: StateAndRef<*>, upgradedContractClass: Class<out UpgradedContract<*, *>>)

    /**
     * Authorise a contract state upgrade.
     * This will remove the upgrade authorisation from the vault.
     */
    fun deauthoriseContractUpgrade(stateAndRef: StateAndRef<*>)
}
