package net.corda.explorer.views.cordapps.cash

import javafx.beans.binding.Bindings
import javafx.beans.binding.BooleanBinding
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.scene.control.*
import javafx.stage.Window
import net.corda.client.fxutils.isNotNull
import net.corda.client.fxutils.map
import net.corda.client.fxutils.unique
import net.corda.client.model.*
import net.corda.core.contracts.*
import net.corda.core.crypto.Party
import net.corda.core.messaging.startFlow
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.OpaqueBytes
import net.corda.explorer.model.CashTransaction
import net.corda.explorer.views.bigDecimalFormatter
import net.corda.explorer.views.byteFormatter
import net.corda.explorer.views.stringConverter
import net.corda.flows.CashCommand
import net.corda.flows.CashFlow
import net.corda.flows.CashFlowResult
import org.controlsfx.dialog.ExceptionDialog
import tornadofx.Fragment
import tornadofx.booleanBinding
import tornadofx.observable
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
    private val rpcProxy by observableValue(NodeMonitorModel::proxyObservable)
    private val myIdentity by observableValue(NetworkIdentityModel::myIdentity)
    private val notaries by observableList(NetworkIdentityModel::notaries)
    private val cash by observableList(ContractStateModel::cash)
    private val executeButton = ButtonType("Execute", ButtonBar.ButtonData.APPLY)

    fun show(window: Window): Unit {
        dialog(window).showAndWait().ifPresent {
            val dialog = Alert(Alert.AlertType.INFORMATION).apply {
                headerText = null
                contentText = "Transaction Started."
                dialogPane.isDisable = true
                initOwner(window)
            }
            dialog.show()
            runAsync {
                rpcProxy.value!!.startFlow(::CashFlow, it).returnValue.toBlocking().first()
            }.ui {
                dialog.contentText = when (it) {
                    is CashFlowResult.Success -> {
                        dialog.alertType = Alert.AlertType.INFORMATION
                        "Transaction Started \nTransaction ID : ${it.transaction?.id} \nMessage : ${it.message}"
                    }
                    is CashFlowResult.Failed -> {
                        dialog.alertType = Alert.AlertType.ERROR
                        it.toString()
                    }
                }
                dialog.dialogPane.isDisable = false
                dialog.dialogPane.scene.window.sizeToScene()
            }.setOnFailed {
                dialog.close()
                ExceptionDialog(it.source.exception).apply { initOwner(window) }.showAndWait()
            }
        }
    }

    private fun dialog(window: Window) = Dialog<CashCommand>().apply {
        dialogPane = root
        initOwner(window)
        setResultConverter {
            val defaultRef = OpaqueBytes(ByteArray(1, { 1 }))
            when (it) {
                executeButton -> when (transactionTypeCB.value) {
                    CashTransaction.Issue -> {
                        val issueRef = if (issueRef.value != null) OpaqueBytes(ByteArray(1, { issueRef.value })) else defaultRef
                        CashCommand.IssueCash(Amount(amount.value, currencyChoiceBox.value), issueRef, partyBChoiceBox.value.legalIdentity, notaries.first().notaryIdentity)
                    }
                    CashTransaction.Pay -> CashCommand.PayCash(Amount(amount.value, Issued(PartyAndReference(issuerChoiceBox.value, defaultRef), currencyChoiceBox.value)), partyBChoiceBox.value.legalIdentity)
                    CashTransaction.Exit -> CashCommand.ExitCash(Amount(amount.value, currencyChoiceBox.value), defaultRef)
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
        transactionTypeCB.items = CashTransaction.values().asList().observable()

        // Party A textfield always display my identity name, not editable.
        partyATextField.isEditable = false
        partyATextField.textProperty().bind(myIdentity.map { it?.legalIdentity?.name ?: "" })
        partyALabel.textProperty().bind(transactionTypeCB.valueProperty().map { it?.partyNameA?.let { "$it : " } })
        partyATextField.visibleProperty().bind(transactionTypeCB.valueProperty().map { it?.partyNameA }.isNotNull())

        // Party B
        partyBLabel.textProperty().bind(transactionTypeCB.valueProperty().map { it?.partyNameB?.let { "$it : " } })
        partyBChoiceBox.apply {
            visibleProperty().bind(transactionTypeCB.valueProperty().map { it?.partyNameB }.isNotNull())
            items = parties.sorted()
            converter = stringConverter { it?.legalIdentity?.name ?: "" }
        }
        // Issuer
        issuerLabel.visibleProperty().bind(transactionTypeCB.valueProperty().isNotNull)
        issuerChoiceBox.apply {
            items = cash.map { it.token.issuer.party }.unique().sorted()
            converter = stringConverter { it.name }
            visibleProperty().bind(transactionTypeCB.valueProperty().map { it == CashTransaction.Pay })
        }
        issuerTextField.apply {
            textProperty().bind(myIdentity.map { it?.legalIdentity?.name })
            visibleProperty().bind(transactionTypeCB.valueProperty().map { it == CashTransaction.Issue || it == CashTransaction.Exit })
            isEditable = false
        }
        // Issue Reference
        issueRefLabel.visibleProperty().bind(transactionTypeCB.valueProperty().map { it == CashTransaction.Issue })

        issueRefTextField.apply {
            textFormatter = byteFormatter().apply { issueRef.bind(this.valueProperty()) }
            visibleProperty().bind(transactionTypeCB.valueProperty().map { it == CashTransaction.Issue })
        }
        // Currency
        currencyLabel.visibleProperty().bind(transactionTypeCB.valueProperty().isNotNull)
        // TODO : Create a currency model to store these values
        currencyChoiceBox.items = FXCollections.observableList(setOf(USD, GBP, CHF).toList())
        currencyChoiceBox.visibleProperty().bind(transactionTypeCB.valueProperty().isNotNull)
        val issuer = Bindings.createObjectBinding({ if (issuerChoiceBox.isVisible) issuerChoiceBox.value else myIdentity.value?.legalIdentity }, arrayOf(myIdentity, issuerChoiceBox.visibleProperty(), issuerChoiceBox.valueProperty()))
        availableAmount.visibleProperty().bind(
                issuer.isNotNull.and(currencyChoiceBox.valueProperty().isNotNull).and(transactionTypeCB.valueProperty().booleanBinding(transactionTypeCB.valueProperty()) { it != CashTransaction.Issue })
        )
        availableAmount.textProperty()
                .bind(Bindings.createStringBinding({
                    val filteredCash = cash.filtered { it.token.issuer.party == issuer.value && it.token.product == currencyChoiceBox.value }
                            .map { it.withoutIssuer().quantity }
                    "${filteredCash.sum()} ${currencyChoiceBox.value?.currencyCode} Available"
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
