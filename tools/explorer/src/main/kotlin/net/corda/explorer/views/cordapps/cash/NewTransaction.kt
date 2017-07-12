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
import net.corda.client.jfx.utils.ChosenList
import net.corda.client.jfx.utils.isNotNull
import net.corda.client.jfx.utils.map
import net.corda.client.jfx.utils.unique
import net.corda.core.contracts.Amount
import net.corda.core.contracts.sumOrNull
import net.corda.core.contracts.withoutIssuer
import net.corda.core.flows.FlowException
import net.corda.core.getOrThrow
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.startFlow
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.explorer.formatters.PartyNameFormatter
import net.corda.explorer.model.CashTransaction
import net.corda.explorer.model.IssuerModel
import net.corda.explorer.model.ReportingCurrencyModel
import net.corda.explorer.views.bigDecimalFormatter
import net.corda.explorer.views.byteFormatter
import net.corda.explorer.views.stringConverter
import net.corda.flows.AbstractCashFlow
import net.corda.flows.CashFlowCommand
import net.corda.flows.IssuerFlow.IssuanceRequester
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
    private val partyBChoiceBox by fxid<ChoiceBox<NodeInfo>>()
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
    private val issuers by observableList(IssuerModel::issuers)
    private val rpcProxy by observableValue(NodeMonitorModel::proxyObservable)
    private val myIdentity by observableValue(NetworkIdentityModel::myIdentity)
    private val notaries by observableList(NetworkIdentityModel::notaries)
    private val cash by observableList(ContractStateModel::cash)
    private val executeButton = ButtonType("Execute", ButtonBar.ButtonData.APPLY)
    private val currencyTypes by observableList(IssuerModel::currencyTypes)
    private val supportedCurrencies by observableList(ReportingCurrencyModel::supportedCurrencies)
    private val transactionTypes by observableList(IssuerModel::transactionTypes)

    private val currencyItems = ChosenList(transactionTypeCB.valueProperty().map {
        when (it) {
            CashTransaction.Pay -> supportedCurrencies
            CashTransaction.Issue,
            CashTransaction.Exit -> currencyTypes
            else -> FXCollections.emptyObservableList()
        }
    })

    fun show(window: Window): Unit {
        newTransactionDialog(window).showAndWait().ifPresent { command ->
            val dialog = Alert(Alert.AlertType.INFORMATION).apply {
                headerText = null
                contentText = "Transaction Started."
                dialogPane.isDisable = true
                initOwner(window)
                show()
            }
            val handle: FlowHandle<AbstractCashFlow.Result> = if (command is CashFlowCommand.IssueCash) {
                rpcProxy.value!!.startFlow(::IssuanceRequester,
                        command.amount,
                        command.recipient,
                        command.issueRef,
                        myIdentity.value!!.legalIdentity,
                        command.notary,
                        command.anonymous)
            } else {
                command.startFlow(rpcProxy.value!!)
            }
            runAsync {
                try {
                    handle.returnValue.getOrThrow()
                } finally {
                    dialog.dialogPane.isDisable = false
                }
            }.ui { it ->
                val stx: SignedTransaction = it.stx
                val type = when (command) {
                    is CashFlowCommand.IssueCash -> "Cash Issued"
                    is CashFlowCommand.ExitCash -> "Cash Exited"
                    is CashFlowCommand.PayCash -> "Cash Paid"
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

    private fun newTransactionDialog(window: Window) = Dialog<CashFlowCommand>().apply {
        dialogPane = root
        initOwner(window)
        setResultConverter {
            // TODO: Enable confidential identities
            val anonymous = false
            val defaultRef = OpaqueBytes.of(1)
            val issueRef = if (issueRef.value != null) OpaqueBytes.of(issueRef.value) else defaultRef
            when (it) {
                executeButton -> when (transactionTypeCB.value) {
                    CashTransaction.Issue -> {
                        CashFlowCommand.IssueCash(Amount.fromDecimal(amount.value, currencyChoiceBox.value), issueRef, partyBChoiceBox.value.legalIdentity, notaries.first().notaryIdentity, anonymous)
                    }
                    CashTransaction.Pay -> CashFlowCommand.PayCash(Amount.fromDecimal(amount.value, currencyChoiceBox.value), partyBChoiceBox.value.legalIdentity, anonymous = anonymous)
                    CashTransaction.Exit -> CashFlowCommand.ExitCash(Amount.fromDecimal(amount.value, currencyChoiceBox.value), issueRef)
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
        partyATextField.textProperty().bind(myIdentity.map { it?.legalIdentity?.let { PartyNameFormatter.short.format(it.name) } ?: "" })
        partyALabel.textProperty().bind(transactionTypeCB.valueProperty().map { it?.partyNameA?.let { "$it : " } })
        partyATextField.visibleProperty().bind(transactionTypeCB.valueProperty().map { it?.partyNameA }.isNotNull())

        // Party B
        partyBLabel.textProperty().bind(transactionTypeCB.valueProperty().map { it?.partyNameB?.let { "$it : " } })
        partyBChoiceBox.apply {
            visibleProperty().bind(transactionTypeCB.valueProperty().map { it?.partyNameB }.isNotNull())
            items = parties.sorted()
            converter = stringConverter { it?.legalIdentity?.let { PartyNameFormatter.short.format(it.name) } ?: "" }
        }
        // Issuer
        issuerLabel.visibleProperty().bind(transactionTypeCB.valueProperty().isNotNull)
        issuerChoiceBox.apply {
            items = issuers.map { it.legalIdentity as Party }.unique().sorted()
            converter = stringConverter { PartyNameFormatter.short.format(it.name) }
            visibleProperty().bind(transactionTypeCB.valueProperty().map { it == CashTransaction.Pay })
        }
        issuerTextField.apply {
            textProperty().bind(myIdentity.map { it?.legalIdentity?.let { PartyNameFormatter.short.format(it.name) } })
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
        val issuer = Bindings.createObjectBinding({ if (issuerChoiceBox.isVisible) issuerChoiceBox.value else myIdentity.value?.legalIdentity }, arrayOf(myIdentity, issuerChoiceBox.visibleProperty(), issuerChoiceBox.valueProperty()))
        availableAmount.visibleProperty().bind(
                issuer.isNotNull.and(currencyChoiceBox.valueProperty().isNotNull).and(transactionTypeCB.valueProperty().booleanBinding(transactionTypeCB.valueProperty()) { it != CashTransaction.Issue })
        )
        availableAmount.textProperty()
                .bind(Bindings.createStringBinding({
                    val filteredCash = cash.filtered { it.token.issuer.party as AbstractParty == issuer.value && it.token.product == currencyChoiceBox.value }
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
