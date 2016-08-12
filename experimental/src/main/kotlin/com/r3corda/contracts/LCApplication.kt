package com.r3corda.contracts

import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.NullPublicKey
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import java.security.PublicKey
import java.time.LocalDate
import java.time.Period
import java.util.*

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Letter of Credit Application
//

// Just a fake program identifier for now. In a real system it could be, for instance, the hash of the program bytecode.
val LC_APPLICATION_PROGRAM_ID = LCApplication()


/**
 *
 */
class LCApplication : Contract {
    // TODO: should reference the content of the legal agreement, not its URI
    override val legalContractReference: SecureHash = SecureHash.sha256("Letter of Credit Application")

    override fun verify(tx: TransactionForContract) {
        val command = tx.commands.requireSingleCommand<LCApplication.Commands>()
        val inputs = tx.inputs.filterIsInstance<State>()
        val outputs = tx.outputs.filterIsInstance<State>()

        when (command.value) {
            is Commands.ApplyForLC -> {
                verifyApply(inputs, outputs, command as AuthenticatedObject<Commands.ApplyForLC>, tx)
            }
            is Commands.Approve -> {
                verifyApprove(inputs, outputs, command as AuthenticatedObject<Commands.Approve>, tx)
            }

        // TODO: Think about how to evolve contracts over time with new commands.
            else -> throw IllegalArgumentException("Unrecognised command")

        }
    }

    private fun verifyApply(inputs: List<State>, outputs: List<State>, command: AuthenticatedObject<Commands.ApplyForLC>, tx: TransactionForContract) {
        val output = outputs.single()
        val applicant = output.props.applicant

        requireThat {
            //TODO - Is this required???
            "the owner must be the issuer" by output.props.issuer.owningKey.equals(output.owner)
            "there is no input state" by inputs.isEmpty()
            "the transaction is signed by the applicant" by (command.signers.contains(applicant.owningKey))
            //TODO - sales contract attached
            //TODO - purchase order attached
            //TODO - application confirms to required template
            "the state is propagated" by (outputs.size == 1)
            "the output status must be pending issuer review" by (output.status.equals(Status.PENDING_ISSUER_REVIEW))
        }
    }

    private fun verifyApprove(inputs: List<State>, outputs: List<State>, command: AuthenticatedObject<Commands.Approve>, tx: TransactionForContract) {
        val input = inputs.single()
        val output = outputs.single()
        val issuer = output.owner

        requireThat {
            //TODO - signed by owner
            "the transaction is signed by the issuer bank (object owner)" by (command.signers.contains(issuer))
            "the input status must be pending issuer review" by (input.status.equals(Status.PENDING_ISSUER_REVIEW))
            "the output status must be approved" by (output.status.equals(Status.APPROVED))
        }
    }

    enum class Status {
        PENDING_ISSUER_REVIEW,
        APPROVED,
        REJECTED
    }

    data class LCApplicationProperties(
            val letterOfCreditApplicationID: String,
            val applicationDate: LocalDate,
            val typeCredit: LocDataStructures.CreditType,
            val issuer: Party,
            val beneficiary: Party,
            val applicant: Party,
            val expiryDate: LocalDate,
            val portLoading: LocDataStructures.Port,
            val portDischarge: LocDataStructures.Port,
            val placePresentation: LocDataStructures.Location,
            val lastShipmentDate: LocalDate,
            val periodPresentation: Period,
            val goods: List<LocDataStructures.PricedGood> = ArrayList(),
            val documentsRequired: List<String> = ArrayList(),
            val invoiceRef: StateRef,
            val amount: Amount<Issued<Currency>>
    ) {
        init {
            if (periodPresentation == null || periodPresentation.isZero) {
                // TODO: set default value???
                // periodPresentation = Period.ofDays(21)
            }
        }
    }

    data class State(
            val owner: PublicKey,
            val status: Status,
            val props: LCApplicationProperties
    ) : ContractState {

        override val contract = LC_APPLICATION_PROGRAM_ID

        override val participants: List<PublicKey>
            get() = listOf(owner)

        // returns true when the actual business properties of the
        // invoice is modified
        fun propertiesChanged(otherState: State): Boolean {
            return (props != otherState.props)
        }

        // iterate over the goods list and sum up the price for each
        fun withoutOwner() = copy(owner = NullPublicKey)
    }

    fun generateApply(props: LCApplicationProperties, notary: Party, purchaseOrder: Attachment): TransactionBuilder {
        val state = State(props.issuer.owningKey, Status.PENDING_ISSUER_REVIEW, props)
        val txBuilder = TransactionType.General.Builder(notary).withItems(state, Command(Commands.ApplyForLC(), props.applicant.owningKey))
        txBuilder.addAttachment(purchaseOrder.id)
        return txBuilder
    }

    fun generateApprove(tx: TransactionBuilder, application: StateAndRef<LCApplication.State>) {
        tx.addInputState(application)
        tx.addOutputState(application.state.data.copy(status = Status.APPROVED))
        tx.addCommand(Commands.Approve(), application.state.data.owner)
    }

    interface Commands : CommandData {
        class ApplyForLC : TypeOnlyCommandData(), Commands
        class Approve : TypeOnlyCommandData(), Commands
    }

}

