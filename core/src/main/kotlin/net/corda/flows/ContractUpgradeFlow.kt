package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.flows.AbstractStateReplacementFlow.Proposal
import net.corda.flows.ContractUpgradeFlow.Acceptor
import net.corda.flows.ContractUpgradeFlow.Instigator

/**
 * A flow to be used for upgrading state objects of an old contract to a new contract.
 *
 * The [Instigator] assembles the transaction for contract upgrade and sends out change proposals to all participants
 * ([Acceptor]) of that state. If participants agree to the proposed change, they each sign the transaction.
 * Finally, [Instigator] sends the transaction containing all signatures back to each participant so they can record it and
 * use the new updated state for future transactions.
 */
object ContractUpgradeFlow {
    @JvmStatic
    fun verify(tx: TransactionForContract) {
        // Contract Upgrade transaction should have 1 input, 1 output and 1 command.
        verify(tx.inputs.single(), tx.outputs.single(), tx.commands.map { Command(it.value, it.signers) }.single())
    }

    @JvmStatic
    fun verify(input: ContractState, output: ContractState, commandData: Command) {
        val command = commandData.value as UpgradeCommand
        val participants: Set<CompositeKey> = input.participants.toSet()
        val keysThatSigned: Set<CompositeKey> = commandData.signers.toSet()
        val upgradedContract = command.upgradedContractClass.newInstance() as UpgradedContract<ContractState, *>
        requireThat {
            "The signing keys include all participant keys" by keysThatSigned.containsAll(participants)
            "Inputs state reference the legacy contract" by (input.contract.javaClass == upgradedContract.legacyContract)
            "Outputs state reference the upgraded contract" by (output.contract.javaClass == command.upgradedContractClass)
            "Output state must be an upgraded version of the input state" by (output == upgradedContract.upgrade(input))
        }
    }

    private fun <OldState : ContractState, NewState : ContractState> assembleBareTx(
            stateRef: StateAndRef<OldState>,
            upgradedContractClass: Class<out UpgradedContract<OldState, NewState>>
    ): TransactionBuilder {
        val contractUpgrade = upgradedContractClass.newInstance()
        return TransactionType.General.Builder(stateRef.state.notary)
                .withItems(stateRef, contractUpgrade.upgrade(stateRef.state.data), Command(UpgradeCommand(upgradedContractClass), stateRef.state.data.participants))
    }

    class Instigator<OldState : ContractState, out NewState : ContractState>(
            originalState: StateAndRef<OldState>,
            newContractClass: Class<out UpgradedContract<OldState, NewState>>
    ) : AbstractStateReplacementFlow.Instigator<OldState, NewState, Class<out UpgradedContract<OldState, NewState>>>(originalState, newContractClass) {

        override fun assembleTx(): Pair<SignedTransaction, Iterable<CompositeKey>> {
            val stx = assembleBareTx(originalState, modification)
                    .signWith(serviceHub.legalIdentityKey)
                    .toSignedTransaction(false)
            return Pair(stx, originalState.state.data.participants)
        }
    }

    class Acceptor(otherSide: Party) : AbstractStateReplacementFlow.Acceptor<Class<out UpgradedContract<ContractState, *>>>(otherSide) {
        @Suspendable
        @Throws(StateReplacementException::class)
        override fun verifyProposal(proposal: Proposal<Class<out UpgradedContract<ContractState, *>>>) {
            // Retrieve signed transaction from our side, we will apply the upgrade logic to the transaction on our side, and verify outputs matches the proposed upgrade.
            val stx = subFlow(FetchTransactionsFlow(setOf(proposal.stateRef.txhash), otherSide)).fromDisk.singleOrNull()
            requireNotNull(stx) { "We don't have a copy of the referenced state" }
            val oldStateAndRef = stx!!.tx.outRef<ContractState>(proposal.stateRef.index)
            val authorisedUpgrade = serviceHub.vaultService.getAuthorisedContractUpgrade(oldStateAndRef.ref) ?: throw IllegalStateException("Contract state upgrade is unauthorised. State hash : ${oldStateAndRef.ref}")
            val proposedTx = proposal.stx.tx
            val expectedTx = assembleBareTx(oldStateAndRef, proposal.modification).toWireTransaction()
            requireThat {
                "The instigator is one of the participants" by oldStateAndRef.state.data.participants.contains(otherSide.owningKey)
                "The proposed upgrade ${proposal.modification.javaClass} is a trusted upgrade path" by (proposal.modification == authorisedUpgrade)
                "The proposed tx matches the expected tx for this upgrade" by (proposedTx == expectedTx)
            }
            ContractUpgradeFlow.verify(oldStateAndRef.state.data, expectedTx.outRef<ContractState>(0).state.data, expectedTx.commands.single())
        }
    }
}
