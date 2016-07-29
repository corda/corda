package com.r3corda.contracts

import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.crypto.toStringShort
import java.security.PublicKey
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

val ACCOUNTRECEIVABLE_PROGRAM_ID = AccountReceivable()

/*


 */



class AccountReceivable : Contract {

    enum class StatusEnum {
        Applied,
        Issued
    }

    data class AccountReceivableProperties(
            val invoiceID: String,
            val exporter: LocDataStructures.Company,
            val buyer: LocDataStructures.Company,
            val currency: Issued<Currency>,
            val invoiceDate: LocalDate,
            val invoiceAmount: Amount<Issued<Currency>>,
            val purchaseDate: LocalDate,
            val maturityDate: LocalDate,
            val discountRate: Double // should be a number between 0 and 1.0. 90% = 0.9

    ) {

        val purchaseAmount: Amount<Issued<Currency>> = invoiceAmount.times((discountRate * 100).toInt()).div(100)
    }

    data class State(
            // technical variables
            override val owner: PublicKey,
            val status: StatusEnum,
            val props: AccountReceivableProperties

    ) : OwnableState {
        override val contract = ACCOUNTRECEIVABLE_PROGRAM_ID

        override val participants: List<PublicKey>
            get() = listOf(owner)

        override fun toString() = "AR owned by ${owner.toStringShort()})"

        fun checkInvoice(invoice: Invoice.State): Boolean {
            val arProps = Helper.invoicePropsToARProps(invoice.props, props.discountRate)
            return props == arProps
        }

        override fun withNewOwner(newOwner: PublicKey) = Pair(Commands.Issue(), copy(owner = newOwner, status = StatusEnum.Issued))

    }

    companion object Helper {
        fun invoicePropsToARProps(invoiceProps: Invoice.InvoiceProperties, discountRate: Double): AccountReceivableProperties {
            return AccountReceivable.AccountReceivableProperties(
                    invoiceProps.invoiceID,
                    invoiceProps.seller, invoiceProps.buyer, invoiceProps.goodCurrency,
                    invoiceProps.invoiceDate, invoiceProps.amount, LocalDate.MIN,
                    invoiceProps.payDate, discountRate)
        }

        fun createARFromInvoice(invoice: Invoice.State, discountRate: Double, notary: Party): TransactionState<AccountReceivable.State> {
            val arProps = invoicePropsToARProps(invoice.props, discountRate)
            val ar = AccountReceivable.State(invoice.owner.owningKey, StatusEnum.Applied, arProps)
            return TransactionState<AccountReceivable.State>(ar, notary)
        }

        fun generateAR(invoice: StateAndRef<Invoice.State>, discountRate: Double, notary: Party): TransactionBuilder {
            if (invoice.state.data.assigned) {
                throw IllegalArgumentException("Cannot build AR with an already assigned invoice")
            }
            val ar = createARFromInvoice(invoice.state.data, discountRate, notary)
            val tx = TransactionType.General.Builder()
            tx.addInputState(invoice)
            tx.addOutputState(invoice.state.data.copy(assigned = true))
            tx.addCommand(Invoice.Commands.Assign(), invoice.state.data.owner.owningKey)
            tx.addOutputState(ar)
            tx.addCommand(AccountReceivable.Commands.Apply(), invoice.state.data.owner.owningKey)
            return tx
        }
    }

    interface Commands : CommandData {
        // Seller offer AR to bank
        class Apply : TypeOnlyCommandData(), Commands

        // Bank check the paper, and accept or reject
        class Issue : TypeOnlyCommandData(), Commands

        // When buyer paid to bank, case close
        class Extinguish : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: TransactionForContract) {
        val command = tx.commands.requireSingleCommand<AccountReceivable.Commands>()

        val time = tx.commands.getTimestampByName("Notary Service", "Seller")?.midpoint ?:
                throw IllegalArgumentException("must be timestamped")

        when (command.value) {
            is AccountReceivable.Commands.Apply -> {
                val invoiceCommand = tx.commands.requireSingleCommand<Invoice.Commands>()
                if (invoiceCommand.value !is Invoice.Commands.Assign) {
                    throw IllegalArgumentException("The invoice command associated must be 'Assign'")
                }

                if (tx.inputs.size != 1) {
                    throw IllegalArgumentException("There must be an input Invoice state")
                }
                val inputInvoice: Invoice.State = tx.inputs.filterIsInstance<Invoice.State>().single()
                if (tx.outputs.size != 2) {
                    throw IllegalArgumentException("There must be two output states")
                }
                val newAR: AccountReceivable.State = tx.outputs.filterIsInstance<AccountReceivable.State>().single()

                requireThat {
                    "AR state must be applied" by (newAR.status == StatusEnum.Applied)
                    "AR properties must match input invoice" by newAR.checkInvoice(inputInvoice)
                    "The discount factor is invalid" by (newAR.props.discountRate >= 0.0 &&
                            newAR.props.discountRate <= 1.0)
                    "the payment date must be in the the future" by
                            (newAR.props.maturityDate.atStartOfDay().toInstant(ZoneOffset.UTC) >= time)
                }
            }
            is AccountReceivable.Commands.Issue -> {
                val oldAR: AccountReceivable.State = tx.inputs.filterIsInstance<AccountReceivable.State>().single()
                val newAR: AccountReceivable.State = tx.outputs.filterIsInstance<AccountReceivable.State>().single()

                requireThat {
                    "input status must be applied" by (oldAR.status == StatusEnum.Applied)
                    "output status must be issued" by (newAR.status == StatusEnum.Issued)
                    "properties must match" by (newAR.props == oldAR.props)
                }
            }
            is AccountReceivable.Commands.Extinguish -> {
                val oldAR: AccountReceivable.State = tx.inputs.filterIsInstance<AccountReceivable.State>().single()
                val newAR: AccountReceivable.State? = tx.outputs.filterIsInstance<AccountReceivable.State>().singleOrNull()

                requireThat {
                    "input status must be issued" by (oldAR.status == StatusEnum.Issued)
                    "output state must not exist" by (newAR == null)
                    "the payment date must be today or in the the past" by
                            (oldAR.props.maturityDate.atStartOfDay().toInstant(ZoneOffset.UTC) <= time)
                }
            }
        }
    }

    // legal Prose
    override val legalContractReference: SecureHash = SecureHash.sha256("AccountReceivable")
}