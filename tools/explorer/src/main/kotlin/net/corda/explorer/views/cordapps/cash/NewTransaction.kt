package net.corda.explorer.views.cordapps.cash

import com.google.common.base.Splitter
import javafx.beans.binding.Bindings
import javafx.beans.binding.BooleanBinding
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.VPos
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.stage.Window
import net.corda.client.jfx.model.*
import net.corda.client.jfx.utils.*
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Amount.Companion.sumOrNull
import net.corda.core.contracts.withoutIssuer
import net.corda.core.flows.FlowException
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.explorer.formatters.PartyNameFormatter
import net.corda.explorer.model.CashTransaction
import net.corda.explorer.model.IssuerModel
import net.corda.explorer.views.bigDecimalFormatter
import net.corda.explorer.views.byteFormatter
import net.corda.explorer.views.stringConverter
import net.corda.explorer.views.toKnownParty
import net.corda.finance.flows.AbstractCashFlow
import net.corda.finance.flows.CashExitFlow
import net.corda.finance.flows.CashExitFlow.ExitRequest
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.finance.flows.CashIssueAndPaymentFlow.IssueAndPaymentRequest
import net.corda.finance.flows.CashPaymentFlow
import net.corda.finance.flows.CashPaymentFlow.PaymentRequest
import net.corda.testing.core.singleIdentityAndCert
import org.controlsfx.dialog.ExceptionDialog
import tornadofx.*
import java.math.BigDecimal
import java.util.*

class NewTransaction : Fragment() {
    override val root by fxml<DialogPane>()
    // Components
    private val transactionTypeCB by fxid<ChoiceBox<CashTransaction>>()
    private val partyATextField by fxid<TextField>()
    private val partyALabel by fxid<Label>()
    private val partyBChoiceBox by fxid<ChoiceBox<PartyAndCertificate>>()
    private val partyBLabel by fxid<Label>()
    private val issuerLabel by fxid<Label>()
    private val issuerTextField by fxid<TextField>()
    private val issuerChoiceBox by fxid<ChoiceBox<Party>>()
    private val issueRefLabel by fxid<Label>()
    private val issueRefTextField by fxid<TextField>()
    private val currencyLabel by fxid<Label>()
    private val currencyChoiceBox by fxid<ChoiceBox<Currency>>()
    private val availableAmount by fxid<Label>()
    private val amountLabel by fxid<Label>()
    private val amountTextField by fxid<TextField>()
    private val amount = SimpleObjectProperty<BigDecimal>()
    private val issueRef = SimpleObjectProperty<Byte>()
    // Inject data
    private val parties by observableList(NetworkIdentityModel::parties)
    private val rpcProxy by observableValue(NodeMonitorModel::proxyObservable)
    private val myIdentity by observableValue(NetworkIdentityModel::myIdentity)
    private val notaries by observableList(NetworkIdentityModel::notaries)
    private val cash by observableList(ContractStateModel::cash)
    private val executeButton = ButtonType("Execute", ButtonBar.ButtonData.APPLY)
    private val currencyTypes by observableList(IssuerModel::currencyTypes)
    private val supportedCurrencies by observableList(IssuerModel::supportedCurrencies)
    private val transactionTypes by observableList(IssuerModel::transactionTypes)
    private val issuers = cash.map { it.token.issuer }

    private val currencyItems = ChosenList(transactionTypeCB.valueProperty().map {
        when (it) {
            CashTransaction.Pay -> supportedCurrencies
            CashTransaction.Issue,
            CashTransaction.Exit -> currencyTypes
            else -> FXCollections.emptyObservableList()
        }
    }, "NewTransactionCurrencyItems")

    fun show(window: Window) {
        newTransactionDialog(window).showAndWait().ifPresent { request ->
            val dialog = Alert(Alert.AlertType.INFORMATION).apply {
                headerText = null
                contentText = "Transaction Started."
                dialogPane.isDisable = true
                initOwner(window)
                show()
            }
            val handle: FlowHandle<AbstractCashFlow.Result> = when (request) {
                is IssueAndPaymentRequest -> rpcProxy.value!!.cordaRPCOps.startFlow(::CashIssueAndPaymentFlow, request)
                is PaymentRequest -> rpcProxy.value!!.cordaRPCOps.startFlow(::CashPaymentFlow, request)
                is ExitRequest -> rpcProxy.value!!.cordaRPCOps.startFlow(::CashExitFlow, request)
                else -> throw IllegalArgumentException("Unexpected request type: $request")
            }
            runAsync {
                try {
                    handle.returnValue.getOrThrow()
                } finally {
                    dialog.dialogPane.isDisable = false
                }
            }.ui {
                val stx: SignedTransaction = it.stx
                val type = when (request) {
                    is IssueAndPaymentRequest -> "Cash Issued"
                    is ExitRequest -> "Cash Exited"
                    is PaymentRequest -> "Cash Paid"
                    else -> throw IllegalArgumentException("Unexpected request type: $request")
                }
                dialog.alertType = Alert.AlertType.INFORMATION
                dialog.dialogPane.content = gridpane {
                    padding = Insets(10.0, 40.0, 10.0, 20.0)
                    vgap = 10.0
                    hgap = 10.0
                    row { label(type) { font = Font.font(font.family, FontWeight.EXTRA_BOLD, font.size + 2) } }
                    row {
                        label("Transaction ID :") { GridPane.setValignment(this, VPos.TOP) }
                        label { text = Splitter.fixedLength(16).split("${stx.id}").joinToString("\n") }
                    }
                }
                dialog.dialogPane.scene.window.sizeToScene()
            }.setOnFailed {
                val ex = it.source.exception
                when (ex) {
                    is FlowException -> {
                        dialog.alertType = Alert.AlertType.ERROR
                        dialog.contentText = ex.message
                    }
                    else -> {
                        dialog.close()
                        ExceptionDialog(ex).apply { initOwner(window) }.showAndWait()
                    }
                }
            }
        }
    }

    private fun selectNotary(): Party = notaries.first().value!!

    private fun newTransactionDialog(window: Window) = Dialog<AbstractCashFlow.AbstractRequest>().apply {
        dialogPane = root
        initOwner(window)
        setResultConverter {
            val anonymous = true
            val defaultRef = OpaqueBytes.of(1)
            val issueRef = if (issueRef.value != null) OpaqueBytes.of(issueRef.value) else defaultRef
            when (it) {
                executeButton -> when (transactionTypeCB.value) {
                    CashTransaction.Issue -> IssueAndPaymentRequest(Amount.fromDecimal(amount.value, currencyChoiceBox.value), issueRef, partyBChoiceBox.value.party, selectNotary(), anonymous)
                    CashTransaction.Pay -> PaymentRequest(Amount.fromDecimal(amount.value, currencyChoiceBox.value), partyBChoiceBox.value.party, anonymous = anonymous, notary = selectNotary())
                    CashTransaction.Exit -> ExitRequest(Amount.fromDecimal(amount.value, currencyChoiceBox.value), issueRef)
                    else -> null
                }
                else -> null
            }
        }
    }

    init {
        // Disable everything when not connected to node.
        val notariesNotNullBinding = Bindings.createBooleanBinding({ notaries.isNotEmpty() }, arrayOf(notaries))
        val enableProperty = myIdentity.isNotNull().and(rpcProxy.isNotNull()).and(notariesNotNullBinding)
        root.disableProperty().bind(enableProperty.not())

        // Transaction Types Choice Box
        transactionTypeCB.items = transactionTypes

        // Party A textfield always display my identity name, not editable.
        partyATextField.isEditable = false
        partyATextField.textProperty().bind(myIdentity.map { it?.let { PartyNameFormatter.short.format(it.name) } ?: "" })
        partyALabel.textProperty().bind(transactionTypeCB.valueProperty().map { it?.partyNameA?.let { "$it : " } })
        partyATextField.visibleProperty().bind(transactionTypeCB.valueProperty().map { it?.partyNameA }.isNotNull())

        // Party B
        partyBLabel.textProperty().bind(transactionTypeCB.valueProperty().map { it?.partyNameB?.let { "$it : " } })
        partyBChoiceBox.apply {
            visibleProperty().bind(transactionTypeCB.valueProperty().map { it?.partyNameB }.isNotNull())
            items = FXCollections.observableList(parties.map { it.singleIdentityAndCert() }).sorted()
            converter = stringConverter { it?.let { PartyNameFormatter.short.format(it.name) } ?: "" }
        }
        // Issuer
        issuerLabel.visibleProperty().bind(transactionTypeCB.valueProperty().isNotNull)
        // TODO This concept should burn (after services removal...)
        issuerChoiceBox.apply {
            items = issuers.map { it.party.owningKey.toKnownParty().value }.filterNotNull().unique().sorted()
            converter = stringConverter { PartyNameFormatter.short.format(it.name) }
            visibleProperty().bind(transactionTypeCB.valueProperty().map { it == CashTransaction.Pay })
        }
        issuerTextField.apply {
            textProperty().bind(myIdentity.map { it?.let { PartyNameFormatter.short.format(it.name) } })
            visibleProperty().bind(transactionTypeCB.valueProperty().map { it == CashTransaction.Issue || it == CashTransaction.Exit })
            isEditable = false
        }
        // Issue Reference
        issueRefLabel.visibleProperty().bind(transactionTypeCB.valueProperty().map { it == CashTransaction.Issue || it == CashTransaction.Exit })

        issueRefTextField.apply {
            textFormatter = byteFormatter().apply { issueRef.bind(this.valueProperty()) }
            visibleProperty().bind(transactionTypeCB.valueProperty().map { it == CashTransaction.Issue || it == CashTransaction.Exit })
        }
        // Currency
        currencyLabel.visibleProperty().bind(transactionTypeCB.valueProperty().isNotNull)
        // TODO : Create a currency model to store these values
        currencyChoiceBox.items = currencyItems
        currencyChoiceBox.visibleProperty().bind(transactionTypeCB.valueProperty().isNotNull)
        val issuer = Bindings.createObjectBinding({ if (issuerChoiceBox.isVisible) issuerChoiceBox.value else myIdentity.value }, arrayOf(myIdentity, issuerChoiceBox.visibleProperty(), issuerChoiceBox.valueProperty()))
        availableAmount.visibleProperty().bind(
                issuer.isNotNull.and(currencyChoiceBox.valueProperty().isNotNull).and(transactionTypeCB.valueProperty().booleanBinding(transactionTypeCB.valueProperty()) { it != CashTransaction.Issue })
        )
        availableAmount.textProperty()
                .bind(Bindings.createStringBinding({
                    val filteredCash = cash.filtered { it.token.issuer.party == issuer.value && it.token.product == currencyChoiceBox.value }
                            .map { it.withoutIssuer() }.sumOrNull()
                    "${filteredCash ?: "None"} Available"
                }, arrayOf(currencyChoiceBox.valueProperty(), issuerChoiceBox.valueProperty())))
        // Amount
        amountLabel.visibleProperty().bind(transactionTypeCB.valueProperty().isNotNull)
        amountTextField.textFormatter = bigDecimalFormatter().apply { amount.bind(this.valueProperty()) }
        amountTextField.visibleProperty().bind(transactionTypeCB.valueProperty().isNotNull)

        // Validate inputs.
        val formValidCondition = arrayOf(
                myIdentity.isNotNull(),
                transactionTypeCB.valueProperty().isNotNull,
                partyBChoiceBox.visibleProperty().not().or(partyBChoiceBox.valueProperty().isNotNull),
                issuerChoiceBox.visibleProperty().not().or(issuerChoiceBox.valueProperty().isNotNull),
                amountTextField.textProperty().isNotEmpty,
                currencyChoiceBox.valueProperty().isNotNull
        ).reduce(BooleanBinding::and)

        // Enable execute button when form is valid.
        root.buttonTypes.add(executeButton)
        root.lookupButton(executeButton).disableProperty().bind(formValidCondition.not())
    }
}
