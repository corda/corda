package com.r3corda.contracts

import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import java.security.PublicKey
import java.time.LocalDate

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Bill of Lading Agreement
//

val BILL_OF_LADING_PROGRAM_ID = BillOfLadingAgreement()

/**
 * A bill of lading is a standard-form document. It is transferable by endorsement (or by lawful transfer of possession)
 * and is a receipt from shipping company regarding the number of packages with a particular weight and markings and a
 * contract for the transportation of same to a port of destination mentioned therein.
 *
 * An order bill of lading is used when shipping merchandise prior to payment, requiring a carrier to deliver the
 * merchandise to the importer, and at the endorsement of the exporter the carrier may transfer title to the importer.
 * Endorsed order bills of lading can be traded as a security or serve as collateral against debt obligations.
 */


class BillOfLadingAgreement : Contract {

    data class BillOfLadingProperties(
            val billOfLadingID: String,
            val issueDate: LocalDate,
            val carrierOwner: Party,
            val nameOfVessel: String,
            val descriptionOfGoods: List<LocDataStructures.Good>,
            val portOfLoading: LocDataStructures.Port,
            val portOfDischarge: LocDataStructures.Port,
            val grossWeight: LocDataStructures.Weight,
            val dateOfShipment: LocalDate?,
            val shipper: LocDataStructures.Company?,
            val notify: LocDataStructures.Person?,
            val consignee: LocDataStructures.Company?
    ) {}

    data class State(
            // technical variables
            override val owner: PublicKey,
            val beneficiary: Party,
            val props: BillOfLadingProperties

    ) : OwnableState {
        override val participants: List<PublicKey>
            get() = listOf(owner)

        override fun withNewOwner(newOwner: PublicKey): Pair<CommandData, OwnableState> {
            return Pair(Commands.TransferPossession(), copy(owner = newOwner))
        }

        override val contract = BILL_OF_LADING_PROGRAM_ID
    }

    interface Commands : CommandData {
        class IssueBL : TypeOnlyCommandData(), Commands
        class TransferAndEndorseBL : TypeOnlyCommandData(), Commands
        class TransferPossession : TypeOnlyCommandData(), Commands
    }

    /** The Invoice contract needs to handle three commands
     * 1: IssueBL --
     * 2: TransferAndEndorseBL --
     * 3: TransferPossession --
     */
    override fun verify(tx: TransactionForContract) {
        val command = tx.commands.requireSingleCommand<BillOfLadingAgreement.Commands>()

        val time = tx.commands.getTimestampByName("Notary Service")?.midpoint
        if (time == null) throw IllegalArgumentException("must be timestamped")

        val txOutputStates: List<BillOfLadingAgreement.State> = tx.outputs.filterIsInstance<BillOfLadingAgreement.State>()
        val txInputStates: List<BillOfLadingAgreement.State> = tx.inputs.filterIsInstance<BillOfLadingAgreement.State>()

        when (command.value) {

            is Commands.IssueBL -> {
                requireThat {
                    "there is no input state" by txInputStates.isEmpty()
                    "the transaction is signed by the carrier" by (command.signers.contains(txOutputStates.single().props.carrierOwner.owningKey))
                }
            }
            is Commands.TransferAndEndorseBL -> {
                requireThat {
                    "the transaction is signed by the beneficiary" by (command.signers.contains(txInputStates.single().beneficiary.owningKey))
                    "the transaction is signed by the state object owner" by (command.signers.contains(txInputStates.single().owner))
                    "the bill of lading agreement properties are unchanged" by (txInputStates.single().props == txOutputStates.single().props)
                }
            }
            is Commands.TransferPossession -> {
                requireThat {
                    "the transaction is signed by the state object owner" by (command.signers.contains(txInputStates.single().owner))
                    //"the state object owner has been updated" by (txInputStates.single().owner != txOutputStates.single().owner)
                    "the beneficiary is unchanged" by (txInputStates.single().beneficiary == txOutputStates.single().beneficiary)
                    "the bill of lading agreement properties are unchanged" by (txInputStates.single().props == txOutputStates.single().props)
                }
            }
        }
    }

    override val legalContractReference: SecureHash = SecureHash.sha256("https://en.wikipedia.org/wiki/Bill_of_lading")

    /**
     * Returns a transaction that issues a Bill of Lading Agreement
     */
    fun generateIssue(owner: PublicKey, beneficiary: Party, props: BillOfLadingProperties, notary: Party? = null): TransactionBuilder {
        val state = State(owner, beneficiary, props)
        return TransactionType.General.Builder(notary = notary).withItems(state, Command(Commands.IssueBL(), props.carrierOwner.owningKey))
    }

    /**
     * Updates the given partial transaction with an input/output/command to reassign ownership of the paper.
     */
    fun generateTransferAndEndorse(tx: TransactionBuilder, BoL: StateAndRef<State>, newOwner: PublicKey, newBeneficiary: Party) {
        tx.addInputState(BoL)
        tx.addOutputState(BoL.state.data.copy(owner = newOwner, beneficiary = newBeneficiary))
        val signers: List<PublicKey> = listOf(BoL.state.data.owner, BoL.state.data.beneficiary.owningKey)
        tx.addCommand(Commands.TransferAndEndorseBL(), signers)
    }

    /**
     * Updates the given partial transaction with an input/output/command to reassign ownership of the paper.
     */
    fun generateTransferPossession(tx: TransactionBuilder, BoL: StateAndRef<State>, newOwner: PublicKey) {
        tx.addInputState(BoL)
        tx.addOutputState(BoL.state.data.copy(owner = newOwner))
//        tx.addOutputState(BoL.state.data.copy().withNewOwner(newOwner))
        tx.addCommand(Commands.TransferPossession(), BoL.state.data.owner)
    }

}
