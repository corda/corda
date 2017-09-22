package net.corda.core.internal

import net.corda.core.contracts.*
import net.corda.core.transactions.TransactionBuilder

object ContractUpgradeUtils {
    fun <OldState : ContractState, NewState : ContractState> assembleBareTx(
            stateRef: StateAndRef<OldState>,
            upgradedContractClass: Class<out UpgradedContract<OldState, NewState>>,
            privacySalt: PrivacySalt
    ): TransactionBuilder {
        val contractUpgrade = upgradedContractClass.newInstance()
        return TransactionBuilder(stateRef.state.notary)
                .withItems(
                        stateRef,
                        StateAndContract(contractUpgrade.upgrade(stateRef.state.data), upgradedContractClass.name),
                        Command(UpgradeCommand(upgradedContractClass.name), stateRef.state.data.participants.map { it.owningKey }),
                        privacySalt
                )
    }
}
