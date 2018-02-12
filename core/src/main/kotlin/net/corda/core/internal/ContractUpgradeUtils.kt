package net.corda.core.internal

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.PrivacySalt
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UpgradedContract
import net.corda.core.node.ServicesForResolution
import net.corda.core.transactions.ContractUpgradeWireTransaction

object ContractUpgradeUtils {
    fun <OldState : ContractState, NewState : ContractState> assembleUpgradeTx(
            stateAndRef: StateAndRef<OldState>,
            upgradedContractClass: Class<out UpgradedContract<OldState, NewState>>,
            privacySalt: PrivacySalt,
            services: ServicesForResolution
    ): ContractUpgradeWireTransaction {
        require(stateAndRef.state.encumbrance == null) { "Cannot upgrade an encumbered state" }

        val legacyContractAttachmentId = services.cordappProvider.getContractAttachmentID(stateAndRef.state.contract)!!
        val upgradedContractAttachmentId = services.cordappProvider.getContractAttachmentID(upgradedContractClass.name)!!
        val inputs = listOf(stateAndRef.ref)
        return ContractUpgradeWireTransaction(
                inputs,
                stateAndRef.state.notary,
                legacyContractAttachmentId,
                upgradedContractClass.name,
                upgradedContractAttachmentId,
                privacySalt
        )
    }
}
