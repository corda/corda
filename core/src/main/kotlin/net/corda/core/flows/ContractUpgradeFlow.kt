package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.internal.ContractUpgradeUtils

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
     *
     * This will store the upgrade authorisation in persistent store, and will be queried by [ContractUpgradeFlow.Acceptor]
     * during contract upgrade process. Invoking this flow indicates the node is willing to upgrade the [StateAndRef] using
     * the [UpgradedContract] class.
     *
     * This flow will NOT initiate the upgrade process. To start the upgrade process, see [Initiate].
     */
    // DOCSTART 1
    @StartableByRPC
    class Authorise(
            val stateAndRef: StateAndRef<*>,
            private val upgradedContractClass: Class<out UpgradedContract<*, *>>
    ) : FlowLogic<Void?>() {
    // DOCEND 1
        @Suspendable
        override fun call(): Void? {
            val upgrade = upgradedContractClass.newInstance()
            if (upgrade.legacyContract != stateAndRef.state.contract) {
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
    // DOCSTART 2
    @StartableByRPC
    class Deauthorise(val stateRef: StateRef) : FlowLogic<Void?>() {
        @Suspendable
        override fun call(): Void? {
    //DOCEND 2
            serviceHub.contractUpgradeService.removeAuthorisedContractUpgrade(stateRef)
            return null
        }
    }

    /**
     * This flow begins the contract upgrade process.
     */
    @InitiatingFlow
    @StartableByRPC
    class Initiate<OldState : ContractState, out NewState : ContractState>(
            originalState: StateAndRef<OldState>,
            newContractClass: Class<out UpgradedContract<OldState, NewState>>
    ) : AbstractStateReplacementFlow.Instigator<OldState, NewState, Class<out UpgradedContract<OldState, NewState>>>(originalState, newContractClass) {

        @Suspendable
        override fun assembleTx(): AbstractStateReplacementFlow.UpgradeTx {
            val baseTx = ContractUpgradeUtils.assembleBareTx(originalState, modification, PrivacySalt())
            val participantKeys = originalState.state.data.participants.map { it.owningKey }.toSet()
            // TODO: We need a much faster way of finding our key in the transaction
            val myKey = serviceHub.keyManagementService.filterMyKeys(participantKeys).single()
            val stx = serviceHub.signInitialTransaction(baseTx, myKey)
            return AbstractStateReplacementFlow.UpgradeTx(stx)
        }
    }
}
