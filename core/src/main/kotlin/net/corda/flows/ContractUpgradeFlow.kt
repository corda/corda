package net.corda.flows

import net.corda.core.contracts.*
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.security.PublicKey

/**
 * A flow to be used for upgrading state objects of an old contract to a new contract.
 *
 * This assembles the transaction for contract upgrade and sends out change proposals to all participants
 * of that state. If participants agree to the proposed change, they each sign the transaction.
 * Finally, the transaction containing all signatures is sent back to each participant so they can record it and
 * use the new updated state for future transactions.
 */
@InitiatingFlow
@StartableByRPC
class ContractUpgradeFlow<OldState : ContractState, out NewState : ContractState>(
        originalState: StateAndRef<OldState>,
        newContractClass: Class<out UpgradedContract<OldState, NewState>>
) : AbstractStateReplacementFlow.Instigator<OldState, NewState, Class<out UpgradedContract<OldState, NewState>>>(originalState, newContractClass) {

    companion object {
        @JvmStatic
        fun verify(tx: TransactionForContract) {
            // Contract Upgrade transaction should have 1 input, 1 output and 1 command.
            verify(tx.inputs.single(), tx.outputs.single(), tx.commands.map { Command(it.value, it.signers) }.single())
        }

        @JvmStatic
        fun verify(input: ContractState, output: ContractState, commandData: Command) {
            val command = commandData.value as UpgradeCommand
            val participantKeys: Set<PublicKey> = input.participants.map { it.owningKey }.toSet()
            val keysThatSigned: Set<PublicKey> = commandData.signers.toSet()
            @Suppress("UNCHECKED_CAST")
            val upgradedContract = command.upgradedContractClass.newInstance() as UpgradedContract<ContractState, *>
            requireThat {
                "The signing keys include all participant keys" using keysThatSigned.containsAll(participantKeys)
                "Inputs state reference the legacy contract" using (input.contract.javaClass == upgradedContract.legacyContract)
                "Outputs state reference the upgraded contract" using (output.contract.javaClass == command.upgradedContractClass)
                "Output state must be an upgraded version of the input state" using (output == upgradedContract.upgrade(input))
            }
        }

        fun <OldState : ContractState, NewState : ContractState> assembleBareTx(
                stateRef: StateAndRef<OldState>,
                upgradedContractClass: Class<out UpgradedContract<OldState, NewState>>
        ): TransactionBuilder {
            val contractUpgrade = upgradedContractClass.newInstance()
            return TransactionType.General.Builder(stateRef.state.notary)
                    .withItems(
                            stateRef,
                            contractUpgrade.upgrade(stateRef.state.data),
                            Command(UpgradeCommand(upgradedContractClass), stateRef.state.data.participants.map { it.owningKey }))
        }
    }

    override fun assembleTx(): AbstractStateReplacementFlow.UpgradeTx {
        val baseTx = assembleBareTx(originalState, modification)
        val participantKeys = originalState.state.data.participants.map { it.owningKey }.toSet()
        // TODO: We need a much faster way of finding our key in the transaction
        val myKey = serviceHub.keyManagementService.filterMyKeys(participantKeys).single()
        val stx = serviceHub.signInitialTransaction(baseTx, myKey)
        return AbstractStateReplacementFlow.UpgradeTx(stx, participantKeys, myKey)
    }
}
