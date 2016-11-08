package net.corda.explorer.views

import javafx.beans.binding.Bindings
import javafx.beans.binding.BooleanBinding
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.*
import javafx.util.converter.BigDecimalStringConverter
import net.corda.client.fxutils.map
import net.corda.client.model.NetworkIdentityModel
import net.corda.client.model.NodeMonitorModel
import net.corda.client.model.observableList
import net.corda.client.model.observableValue
import net.corda.core.contracts.*
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.OpaqueBytes
import net.corda.explorer.model.CashTransaction
import net.corda.node.services.messaging.CordaRPCOps
import net.corda.node.services.messaging.startProtocol
import net.corda.protocols.CashCommand
import net.corda.protocols.CashProtocol
import net.corda.protocols.TransactionBuildResult
import org.controlsfx.dialog.ExceptionDialog
import tornadofx.View
import java.math.BigDecimal
import java.util.*
import java.util.regex.Pattern

class NewTransaction : View() {
    override val root: Parent by fxml()

    private val partyATextField: TextField by fxid()
    private val partyBChoiceBox: ChoiceBox<NodeInfo> by fxid()
    private val partyALabel: Label by fxid()
    private val partyBLabel: Label by fxid()
    private val amountLabel: Label by fxid()

    private val executeButton: Button by fxid()

    private val transactionTypeCB: ChoiceBox<CashTransaction> by fxid()
    private val amount: TextField by fxid()
    private val currency: ChoiceBox<Currency> by fxid()
    private val issueRefLabel: Label by fxid()
    private val issueRefTextField: TextField by fxid()

    // Inject data
    private val parties: ObservableList<NodeInfo> by observableList(NetworkIdentityModel::parties)
    private val rpcProxy: ObservableValue<CordaRPCOps?> by observableValue(NodeMonitorModel::proxyObservable)
    private val myIdentity: ObservableValue<NodeInfo?> by observableValue(NetworkIdentityModel::myIdentity)
    private val notaries: ObservableList<NodeInfo> by observableList(NetworkIdentityModel::notaries)

    private fun ObservableValue<*>.isNotNull(): BooleanBinding {
        return Bindings.createBooleanBinding({ this.value != null }, arrayOf(this))
    }

    private fun resetScreen() {
        partyBChoiceBox.valueProperty().set(null)
        transactionTypeCB.valueProperty().set(null)
        currency.valueProperty().set(null)
        amount.clear()
    }

    init {
        // Disable everything when not connected to node.
        val notariesNotNullBinding = Bindings.createBooleanBinding({ notaries.isNotEmpty() }, arrayOf(notaries))
        val enableProperty = myIdentity.isNotNull().and(rpcProxy.isNotNull()).and(notariesNotNullBinding)
        root.disableProperty().bind(enableProperty.not())
        transactionTypeCB.items = FXCollections.observableArrayList(CashTransaction.values().asList())

        // Party A textfield always display my identity name, not editable.
        partyATextField.isEditable = false
        partyATextField.textProperty().bind(myIdentity.map { it?.legalIdentity?.name ?: "" })
        partyALabel.textProperty().bind(transactionTypeCB.valueProperty().map { it?.partyNameA?.let { "$it : " } })
        partyATextField.visibleProperty().bind(transactionTypeCB.valueProperty().map { it?.partyNameA }.isNotNull())

        partyBLabel.textProperty().bind(transactionTypeCB.valueProperty().map { it?.partyNameB?.let { "$it : " } })
        partyBChoiceBox.apply {
            visibleProperty().bind(transactionTypeCB.valueProperty().map { it?.partyNameB }.isNotNull())
            partyBChoiceBox.items = parties
            converter = stringConverter { it?.legalIdentity?.name ?: "" }
        }

        // BigDecimal text Formatter, restricting text box input to decimal values.
        val textFormatter = Pattern.compile("-?((\\d*)|(\\d+\\.\\d*))").run {
            TextFormatter<BigDecimal>(BigDecimalStringConverter(), null) { change ->
                val newText = change.controlNewText
                if (matcher(newText).matches()) change else null
            }
        }
        amount.textFormatter = textFormatter

        // Hide currency and amount fields when transaction type is not specified.
        // TODO : Create a currency model to store these values
        currency.items = FXCollections.observableList(setOf(USD, GBP, CHF).toList())
        currency.visibleProperty().bind(transactionTypeCB.valueProperty().isNotNull)
        amount.visibleProperty().bind(transactionTypeCB.valueProperty().isNotNull)
        amountLabel.visibleProperty().bind(transactionTypeCB.valueProperty().isNotNull)
        issueRefLabel.visibleProperty().bind(transactionTypeCB.valueProperty().isNotNull)
        issueRefTextField.visibleProperty().bind(transactionTypeCB.valueProperty().isNotNull)

        // Validate inputs.
        val formValidCondition = arrayOf(
                myIdentity.isNotNull(),
                transactionTypeCB.valueProperty().isNotNull,
                partyBChoiceBox.visibleProperty().not().or(partyBChoiceBox.valueProperty().isNotNull),
                textFormatter.valueProperty().isNotNull,
                textFormatter.valueProperty().isNotEqualTo(BigDecimal.ZERO),
                currency.valueProperty().isNotNull
        ).reduce(BooleanBinding::and)

        // Enable execute button when form is valid.
        executeButton.disableProperty().bind(formValidCondition.not())
        executeButton.setOnAction { event ->
            // Null checks to ensure these observable values are set, execute button should be disabled if any of these value are null, this extra checks are for precaution and getting non-nullable values without using !!.
            myIdentity.value?.let { myIdentity ->
                // TODO : Allow user to chose which notary to use?
                notaries.first()?.let { notary ->
                    rpcProxy.value?.let { rpcProxy ->
                        Triple(myIdentity, notary, rpcProxy)
                    }
                }
            }?.let {
                val (myIdentity, notary, rpcProxy) = it
                transactionTypeCB.value?.let {
                    // Default issuer reference to 1 if not specified.
                    val issueRef = OpaqueBytes(if (issueRefTextField.text.trim().isNotBlank()) issueRefTextField.text.toByteArray() else ByteArray(1, { 1 }))
                    // TODO : Change these commands into individual RPC methods instead of using executeCommand.
                    val command = when (it) {
                        CashTransaction.Issue -> CashCommand.IssueCash(Amount(textFormatter.value, currency.value), issueRef, partyBChoiceBox.value.legalIdentity, notary.notaryIdentity)
                        CashTransaction.Pay -> CashCommand.PayCash(Amount(textFormatter.value, Issued(PartyAndReference(myIdentity.legalIdentity, issueRef), currency.value)), partyBChoiceBox.value.legalIdentity)
                        CashTransaction.Exit -> CashCommand.ExitCash(Amount(textFormatter.value, currency.value), issueRef)
                    }
                    val dialog = Alert(Alert.AlertType.INFORMATION).apply {
                        headerText = null
                        contentText = "Transaction Started."
                        dialogPane.isDisable = true
                        initOwner((event.target as Node).scene.window)
                    }
                    dialog.show()
                    runAsync {
                        rpcProxy.startProtocol(::CashProtocol, command).returnValue.toBlocking().first()
                    }.ui {
                        dialog.contentText = when (it) {
                            is TransactionBuildResult.ProtocolStarted -> {
                                dialog.alertType = Alert.AlertType.INFORMATION
                                dialog.setOnCloseRequest { resetScreen() }
                                "Transaction Started \nTransaction ID : ${it.transaction?.id} \nMessage : ${it.message}"
                            }
                            is TransactionBuildResult.Failed -> {
                                dialog.alertType = Alert.AlertType.ERROR
                                it.toString()
                            }
                        }
                        dialog.dialogPane.isDisable = false
                        dialog.dialogPane.scene.window.sizeToScene()
                    }.setOnFailed {
                        dialog.close()
                        ExceptionDialog(it.source.exception).apply {
                            initOwner((event.target as Node).scene.window)
                        }.showAndWait()
                    }
                }
            }
        }
        // Remove focus from textfield when click on the blank area.
        root.setOnMouseClicked { e -> root.requestFocus() }
    }
}
