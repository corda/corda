package net.corda.core.internal

import net.corda.core.DeleteForDJVM
import net.corda.core.contracts.*
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.services.AttachmentId
import net.corda.core.transactions.ContractUpgradeWireTransaction

@DeleteForDJVM
object ContractUpgradeUtils {
    fun <OldState : ContractState, NewState : ContractState> assembleUpgradeTx(
            stateAndRef: StateAndRef<OldState>,
            upgradedContractClass: Class<out UpgradedContract<OldState, NewState>>,
            privacySalt: PrivacySalt,
            services: ServicesForResolution
    ): ContractUpgradeWireTransaction {
        require(stateAndRef.state.encumbrance == null) { "Upgrading an encumbered state is not yet supported" }
        val legacyConstraint = stateAndRef.state.constraint
        val legacyContractAttachmentId = when (legacyConstraint) {
            is HashAttachmentConstraint -> legacyConstraint.attachmentId
            else -> getContractAttachmentId(stateAndRef.state.contract, services)
        }
        val upgradedContractAttachmentId = getContractAttachmentId(upgradedContractClass.name, services)

        val inputs = listOf(stateAndRef.ref)
        return ContractUpgradeTransactionBuilder(
                inputs,
                stateAndRef.state.notary,
                legacyContractAttachmentId,
                upgradedContractClass.name,
                upgradedContractAttachmentId,
                privacySalt
        ).build()
    }

    private fun getContractAttachmentId(name: ContractClassName, services: ServicesForResolution): AttachmentId {
        return services.cordappProvider.getContractAttachmentID(name)
                ?: throw IllegalStateException("Attachment not found for contract: $name")
    }
}