package net.corda.contracts

import net.corda.contracts.asset.sumCashBy
import net.corda.core.contracts.*
import net.corda.core.crypto.Party
import net.corda.core.crypto.PublicKeyTree
import net.corda.core.crypto.SecureHash
import net.corda.core.days
import net.corda.core.transactions.TransactionBuilder
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneOffset
import java.util.*

val LOC_PROGRAM_ID = LOC()

/** LOC contract - consists of the following commands 1. Issue 2. DemandPresentation 3. Termination ***/


class LOC : Contract {


    data class Company(
            val name: String,
            val address: String,
            val phone: String?
    )

    data class LOCProperties(
            val letterOfCreditID: String,
            val applicationDate: LocalDate,
            val issueDate: LocalDate,
            val typeCredit: LocDataStructures.CreditType,
            val amount: Amount<Issued<Currency>>,
            val invoiceRef: StateRef,
            val expiryDate: LocalDate,
            val portLoading: LocDataStructures.Port,
            val portDischarge: LocDataStructures.Port,
            val descriptionGoods: List<LocDataStructures.PricedGood>,
            val placePresentation: LocDataStructures.Location,
            val latestShip: LocalDate,
            val periodPresentation: Period,
            val beneficiary: Party,
            val issuingbank: Party,
            val appplicant: Party

    ) {
    }

    data class State(
            // technical variables
            val beneficiaryPaid: Boolean,
            val issued: Boolean,
            val terminated: Boolean,
            val props: LOC.LOCProperties

    ) : ContractState {
        override val contract = LOC_PROGRAM_ID

        override val participants: List<PublicKeyTree>
            get() = listOf()
    }

    interface Commands : CommandData {
        class Issuance : TypeOnlyCommandData(), Commands
        class DemandPresentation : TypeOnlyCommandData(), Commands
        class Termination : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: TransactionForContract) {
        val command = tx.commands.requireSingleCommand<LOC.Commands>()

        val time = tx.timestamp?.midpoint
        if (time == null) throw IllegalArgumentException("must be timestamped")

        when (command.value) {
            is Commands.Issuance -> {

                // val LOCappInput: LOCapp.State = tx.inStates.filterIsInstance<LOCapp.State>().single()
                val LOCissueOutput: LOC.State = tx.outputs.filterIsInstance<LOC.State>().single()

                requireThat {
                    // "there is no input state" by !tx.inStates.filterIsInstance<State>().isEmpty() TODO: verify if LOC application is submitted
                    //"LOC application has not been submitted" by (tx.inStates.filterIsInstance<LOCapp.State>().count()  == 1)
                    "the transaction is not signed by the issuing bank" by (command.signers.contains(LOCissueOutput.props.issuingbank.owningKey))
                    "the LOC must be Issued" by (LOCissueOutput.issued == true)
                    "Demand Presentation must not be preformed successfully" by (LOCissueOutput.beneficiaryPaid == false)
                    "LOC must not be terminated" by (LOCissueOutput.terminated == false)
                    "the period of presentation must be a positive number" by (!LOCissueOutput.props.periodPresentation.isNegative && !LOCissueOutput.props.periodPresentation.isZero)
                }
            }
            is Commands.DemandPresentation -> {


                val LOCInput: LOC.State = tx.inputs.filterIsInstance<LOC.State>().single()
                val invoiceInput: Invoice.State = tx.inputs.filterIsInstance<Invoice.State>().single()

                val LOCdemandOutput: LOC.State = tx.outputs.filterIsInstance<LOC.State>().single()
                val BOLtransferOutput: BillOfLadingAgreement.State = tx.outputs.filterIsInstance<BillOfLadingAgreement.State>().single()

                val CashpayOutput = tx.outputs.sumCashBy(LOCdemandOutput.props.beneficiary.owningKey)

                requireThat {

                    "there is no input state" by !tx.inputs.filterIsInstance<State>().isEmpty()
                    "the transaction is signed by the issuing bank" by (command.signers.contains(LOCdemandOutput.props.issuingbank.owningKey))
                    "the transaction is signed by the Beneficiary" by (command.signers.contains(LOCdemandOutput.props.beneficiary.owningKey))
                    "the LOC properties do not remain the same" by (LOCInput.props.equals(LOCdemandOutput.props))
                    "the LOC expiry date has passed" by (LOCdemandOutput.props.expiryDate.atStartOfDay().toInstant(ZoneOffset.UTC) > time)
                    "the shipment is late" by (LOCdemandOutput.props.latestShip > (BOLtransferOutput.props.dateOfShipment ?: BOLtransferOutput.props.issueDate))
                    "the cash state has not been transferred" by (CashpayOutput.token.equals(invoiceInput.amount.token) && CashpayOutput.quantity >= (invoiceInput.amount.quantity))
                    "the bill of lading has not been transferred" by (LOCdemandOutput.props.appplicant.owningKey.equals(BOLtransferOutput.beneficiary.owningKey))
                    "the beneficiary has not been paid, status not changed" by (LOCdemandOutput.beneficiaryPaid == true)
                    "the LOC must be Issued" by (LOCdemandOutput.issued == true)
                    "LOC must not be terminated" by (LOCdemandOutput.terminated == false)
                    //  "the presentation is late" by (time <= (LOCdemandOutput.props.periodPresentation.addTo(LOCdemandOutput.props.issueDate) as LocalDate).atStartOfDay().toInstant(ZoneOffset.UTC) )

                }
            }
            is Commands.Termination -> {

                val LOCterminateOutput: LOC.State = tx.outputs.filterIsInstance<LOC.State>().single()
                //val CashpayOutput2: Cash.State = tx.outputs.filterIsInstance<Cash.State>().single()
                val CashpayOutput = tx.outputs.sumCashBy(LOCterminateOutput.props.issuingbank.owningKey)

                val LOCinput: LOC.State = tx.inputs.filterIsInstance<LOC.State>().single()

                requireThat {
                    "the transaction is signed by the issuing bank" by (command.signers.contains(LOCterminateOutput.props.issuingbank.owningKey))
                    //"the transaction is signed by the applicant" by (command.signers.contains(LOCterminateOutput.props.appplicant.owningKey))
                    "the cash state has not been transferred" by (CashpayOutput.token.equals(LOCterminateOutput.props.amount.token) && CashpayOutput.quantity >= (LOCterminateOutput.props.amount.quantity))
                    "the beneficiary has not been paid, status not changed" by (LOCterminateOutput.beneficiaryPaid == true)
                    "the LOC must be Issued" by (LOCterminateOutput.issued == true)
                    "LOC should be terminated" by (LOCterminateOutput.terminated == true)
                    "the LOC properties do not remain the same" by (LOCinput.props.equals(LOCterminateOutput.props))

                }
            }
        }

    }

    override val legalContractReference: SecureHash = SecureHash.sha256("LOC")

    fun generateIssue(beneficiaryPaid: Boolean, issued: Boolean, terminated: Boolean, props: LOCProperties, notary: Party): TransactionBuilder {
        val state = State(beneficiaryPaid, issued, terminated, props)
        val builder = TransactionType.General.Builder(notary = notary)
        builder.setTime(Instant.now(), 1.days)
        return builder.withItems(state, Command(Commands.Issuance(), props.issuingbank.owningKey))
    }


}
