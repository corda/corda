package net.corda.contracts

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.corda.core.contracts.*
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.transactions.TransactionBuilder
import java.security.PublicKey
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Invoice
//

val INVOICE_PROGRAM_ID = Invoice()

// TODO: Any custom exceptions needed?

/**
 * An invoice is a document that describes a trade between a buyer and a seller. It is issued on a particular date,
 * it lists goods being sold by the seller, the cost of each good and the total amount owed by the buyer and when
 * the buyer expects to be paid by.
 *
 * In the trade finance world, invoices are used to create other contracts (for example AccountsReceivable), newly
 * created invoices start off with a status of "unassigned", once they're used to create other contracts the status
 * is changed to "assigned". This ensures that an invoice is used only once when creating a financial product like
 * AccountsReceivable.
 *
 */

class Invoice : Contract {


    data class InvoiceProperties(
            val invoiceID: String,
            val seller: LocDataStructures.Company,
            val buyer: LocDataStructures.Company,
            val invoiceDate: LocalDate,
            val term: Long,
            val goods: List<LocDataStructures.PricedGood> = ArrayList()
    ) {
        init {
            require(term > 0) { "the term must be a positive number" }
            require(goods.isNotEmpty()) { "there must be goods assigned to the invoice" }
        }

        // returns the single currency used by the goods list
        val goodCurrency: Issued<Currency> get() = goods.map { it.unitPrice.token }.distinct().single()

        // iterate over the goods list and sum up the price for each
        val amount: Amount<Issued<Currency>> get() = goods.map { it.totalPrice() }.sumOrZero(goodCurrency)

        // add term to invoice date to determine the payDate
        val payDate: LocalDate get() {
            return invoiceDate.plusDays(term)
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class State(
            // technical variables
            val owner: Party,
            val buyer: Party,
            val assigned: Boolean,
            val props: InvoiceProperties,
            override val linearId: UniqueIdentifier = UniqueIdentifier()
    ) : LinearState {

        override val contract = INVOICE_PROGRAM_ID

        override val participants: List<PublicKey>
            get() = listOf(owner.owningKey)

        // returns true when the actual business properties of the
        // invoice is modified
        fun propertiesChanged(otherState: State): Boolean {
            return (props != otherState.props)
        }

        fun generateInvoice(notary: Party? = null): TransactionBuilder = Invoice().generateInvoice(props, owner, buyer, notary)

        // iterate over the goods list and sum up the price for each
        val amount: Amount<Issued<Currency>> get() = props.amount

        override fun isRelevant(ourKeys: Set<PublicKey>): Boolean {
            return owner.owningKey in ourKeys || buyer.owningKey in ourKeys
        }
    }

    fun generateInvoice(props: InvoiceProperties, owner: Party, buyer: Party, notary: Party? = null): TransactionBuilder {
        val state = State(owner, buyer, false, props)
        return TransactionType.General.Builder(notary = notary).withItems(state, Command(Commands.Issue(), listOf(owner.owningKey)))
    }

    interface Commands : CommandData {
        class Issue : TypeOnlyCommandData(), Commands
        class Assign : TypeOnlyCommandData(), Commands
        class Extinguish : TypeOnlyCommandData(), Commands
    }

    /** The Invoice contract needs to handle three commands
     * 1: Issue -- the creation of the Invoice contract. We need to confirm that the correct
     *             party signed the contract and that the relevant fields are populated with valid data.
     * 2: Assign -- the invoice is used to create another type of Contract. The assigned boolean has to change from
     *             false to true.
     * 3: Extinguish -- the invoice is deleted. Proper signing is required.
     *
     */
    override fun verify(tx: TransactionForContract) {
        val command = tx.commands.requireSingleCommand<Invoice.Commands>()

        val time = tx.timestamp?.midpoint ?:
                throw IllegalArgumentException("must be timestamped")

        when (command.value) {
            is Commands.Issue -> {
                if (tx.outputs.size != 1) {
                    throw IllegalArgumentException("Failed requirement: during issuance of the invoice, only " +
                            "one output invoice state should be include in the transaction. " +
                            "Number of output states included was " + tx.outputs.size)
                }
                val issueOutput: Invoice.State = tx.outputs.filterIsInstance<Invoice.State>().single()

                requireThat {
                    "there is no input state" by tx.inputs.filterIsInstance<State>().isEmpty()
                    "the transaction is signed by the invoice owner" by (command.signers.contains(issueOutput.owner.owningKey))
                    "the buyer and seller must be different" by (issueOutput.props.buyer.name != issueOutput.props.seller.name)
                    "the invoice must not be assigned" by (issueOutput.assigned == false)
                    "the invoice ID must not be blank" by (issueOutput.props.invoiceID.length > 0)
                    "the term must be a positive number" by (issueOutput.props.term > 0)
                    "the payment date must be in the future" by (issueOutput.props.payDate.atStartOfDay().toInstant(ZoneOffset.UTC) > time)
                    "there must be goods associated with the invoice" by (issueOutput.props.goods.isNotEmpty())
                    "the invoice amount must be non-zero" by (issueOutput.amount.quantity > 0)
                }
            }
            is Commands.Assign -> {
                val assignInput: Invoice.State = tx.inputs.filterIsInstance<Invoice.State>().single()
                val assignOutput: Invoice.State = tx.outputs.filterIsInstance<Invoice.State>().single()

                requireThat {
                    "input state owner must be the same as the output state owner" by (assignInput.owner == assignOutput.owner)
                    "the transaction must be signed by the owner" by (command.signers.contains(assignInput.owner.owningKey))
                    "the invoice properties must remain unchanged" by (!assignOutput.propertiesChanged(assignInput))
                    "the input invoice must not be assigned" by (assignInput.assigned == false)
                    "the output invoice must be assigned" by (assignOutput.assigned == true)
                    "the payment date must be in the future" by (assignInput.props.payDate.atStartOfDay().toInstant(ZoneOffset.UTC) > time)
                }
            }
            is Commands.Extinguish -> {
                val extinguishInput: Invoice.State = tx.inputs.filterIsInstance<Invoice.State>().single()
                val extinguishOutput: Invoice.State? = tx.outputs.filterIsInstance<Invoice.State>().singleOrNull()

                requireThat {
                    "there shouldn't be an output state" by (extinguishOutput == null)
                    "the transaction must be signed by the owner" by (command.signers.contains(extinguishInput.owner.owningKey))
                    //    "the payment date must be today or in the past" by (extinguishInput.props.payDate.atStartOfDay().toInstant(ZoneOffset.UTC) < time)
                }
            }
        }
    }

    override val legalContractReference: SecureHash = SecureHash.sha256("Invoice")
}
