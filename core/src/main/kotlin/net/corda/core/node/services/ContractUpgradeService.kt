package net.corda.core.node.services

import net.corda.core.DoNotImplement
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UpgradedContract
import net.corda.core.flows.ContractUpgradeFlow

/**
 * The [ContractUpgradeService] is responsible for securely upgrading contract state objects according to
 * a specified and mutually agreed (amongst participants) contract version.
 * See also [ContractUpgradeFlow] to understand the workflow associated with contract upgrades.
 */
@DoNotImplement
interface ContractUpgradeService {

    /** Get contracts we would be willing to upgrade the suggested contract to. */
    fun getAuthorisedContractUpgrade(ref: StateRef): String?

    /** Store authorised state ref and associated UpgradeContract class */
    fun storeAuthorisedContractUpgrade(ref: StateRef, upgradedContractClass: Class<out UpgradedContract<*, *>>)

    /** Remove a previously authorised state ref */
    fun removeAuthorisedContractUpgrade(ref: StateRef)
}
