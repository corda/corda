package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.security.PublicKey

/**
 * A flow to be used for authorising and upgrading state objects of an old contract to a new contract.
 *
 * This assembles the transaction for contract upgrade and sends out change proposals to all participants
 * of that state. If participants agree to the proposed change, they each sign the transaction.
 * Finally, the transaction containing all signatures is sent back to each participant so they can record it and
 * use the new updated state for future transactions.
 */
object ContractUpgradeFlow {

    /**
     * Authorise a contract state upgrade.
     * This will store the upgrade authorisation in persistent store, and will be queried by [ContractUpgradeFlow.Acceptor] during contract upgrade process.
     * Invoking this flow indicates the node is willing to upgrade the [StateAndRef] using the [UpgradedContract] class.
     * This method will NOT initiate the upgrade process. To start the upgrade process, see [Initiator].
     */
    @StartableByRPC
    class Authorise(
            val stateAndRef: StateAndRef<*>,
            private val upgradedContractClass: Class<out UpgradedContract<*, *>>
        ) : FlowLogic<Void?>() {
        override fun call(): Void? {
            val upgrade = upgradedContractClass.newInstance()
            if (upgrade.legacyContract != stateAndRef.state.data.contract.javaClass) {
                throw FlowException("The contract state cannot be upgraded using provided UpgradedContract.")
            }
            serviceHub.contractUpgradeService.storeAuthorisedContractUpgrade(stateAndRef.ref, upgradedContractClass)
            return null
        }

    }

    /**
     * Deauthorise a contract state upgrade.
     * This will remove the upgrade authorisation from persistent store (and prevent any further upgrade)
     */
    @StartableByRPC
    class Deauthorise(
            val stateRef: StateRef
    ) : FlowLogic< Void?>() {
        override fun call(): Void? {
            serviceHub.contractUpgradeService.removeAuthorisedContractUpgrade(stateRef)
            return null
        }
    }

    @InitiatingFlow
    @StartableByRPC
    class Initiator<OldState : ContractState, out NewState : ContractState>(
            originalState: StateAndRef<OldState>,
            newContractClass: Class<out UpgradedContract<OldState, NewState>>
    ) : AbstractStateReplacementFlow.Instigator<OldState, NewState, Class<out UpgradedContract<OldState, NewState>>>(originalState, newContractClass) {

        companion object {
            fun <OldState : ContractState, NewState : ContractState> assembleBareTx(
                    stateRef: StateAndRef<OldState>,
                    upgradedContractClass: Class<out UpgradedContract<OldState, NewState>>,
                    privacySalt: PrivacySalt
            ): TransactionBuilder {
                val contractUpgrade = upgradedContractClass.newInstance()
                return TransactionBuilder(stateRef.state.notary)
                        .withItems(
                                stateRef,
                                contractUpgrade.upgrade(stateRef.state.data),
                                Command(UpgradeCommand(upgradedContractClass), stateRef.state.data.participants.map { it.owningKey }),
                                privacySalt
                        )
            }
        }

        @Suspendable
        override fun assembleTx(): AbstractStateReplacementFlow.UpgradeTx {
            val baseTx = assembleBareTx(originalState, modification, PrivacySalt())
            val participantKeys = originalState.state.data.participants.map { it.owningKey }.toSet()
            // TODO: We need a much faster way of finding our key in the transaction
            val myKey = serviceHub.keyManagementService.filterMyKeys(participantKeys).single()
            val stx = serviceHub.signInitialTransaction(baseTx, myKey)
            return AbstractStateReplacementFlow.UpgradeTx(stx, participantKeys, myKey)
        }
    }

    @StartableByRPC
    @InitiatedBy(ContractUpgradeFlow.Initiator::class)
    class Acceptor(otherSide: Party) : AbstractStateReplacementFlow.Acceptor<Class<out UpgradedContract<ContractState, *>>>(otherSide) {

        companion object {
            @JvmStatic
            fun verify(tx: LedgerTransaction) {
                // Contract Upgrade transaction should have 1 input, 1 output and 1 command.
                verify(tx.inputStates.single(),
                        tx.outputStates.single(),
                        tx.commandsOfType<UpgradeCommand>().single())
            }

            @JvmStatic
            fun verify(input: ContractState, output: ContractState, commandData: Command<UpgradeCommand>) {
                val command = commandData.value
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
        }

        @Suspendable
        @Throws(StateReplacementException::class)
        override fun verifyProposal(stx: SignedTransaction, proposal: AbstractStateReplacementFlow.Proposal<Class<out UpgradedContract<ContractState, *>>>) {
            // Retrieve signed transaction from our side, we will apply the upgrade logic to the transaction on our side, and
            // verify outputs matches the proposed upgrade.
            val ourSTX = serviceHub.validatedTransactions.getTransaction(proposal.stateRef.txhash)
            requireNotNull(ourSTX) { "We don't have a copy of the referenced state" }
            val oldStateAndRef = ourSTX!!.tx.outRef<ContractState>(proposal.stateRef.index)
            val authorisedUpgrade = serviceHub.contractUpgradeService.getAuthorisedContractUpgrade(oldStateAndRef.ref) ?:
                    throw IllegalStateException("Contract state upgrade is unauthorised. State hash : ${oldStateAndRef.ref}")
            val proposedTx = stx.tx
            val expectedTx = ContractUpgradeFlow.Initiator.assembleBareTx(oldStateAndRef, proposal.modification, proposedTx.privacySalt).toWireTransaction()
            requireThat {
                "The instigator is one of the participants" using (otherSide in oldStateAndRef.state.data.participants)
                "The proposed upgrade ${proposal.modification.javaClass} is a trusted upgrade path" using (proposal.modification == authorisedUpgrade)
                "The proposed tx matches the expected tx for this upgrade" using (proposedTx == expectedTx)
            }
            ContractUpgradeFlow.Acceptor.verify(
                    oldStateAndRef.state.data,
                    expectedTx.outRef<ContractState>(0).state.data,
                    expectedTx.toLedgerTransaction(serviceHub).commandsOfType<UpgradeCommand>().single())
        }
    }
}
