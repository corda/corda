/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.explorer.views

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TextField
import net.corda.client.jfx.model.objectProperty
import net.corda.client.jfx.model.observableList
import net.corda.client.jfx.utils.map
import net.corda.explorer.model.CordaView
import net.corda.explorer.model.IssuerModel
import net.corda.explorer.model.ReportingCurrencyModel
import net.corda.explorer.model.SettingsModel
import java.util.*

// Allow user to configure preferences, e.g Reporting currency, full screen mode etc.
class Settings : CordaView() {
    override val root by fxml<Parent>()
    override val icon = FontAwesomeIcon.COGS

    // Inject Data.
    private val currencies by observableList(IssuerModel::supportedCurrencies)
    private val reportingCurrencies by objectProperty(SettingsModel::reportingCurrencyProperty)
    private val rememberMe by objectProperty(SettingsModel::rememberMeProperty)
    private val fullscreen by objectProperty(SettingsModel::fullscreenProperty)
    private val host by objectProperty(SettingsModel::hostProperty)
    private val port by objectProperty(SettingsModel::portProperty)

    // Components.
    private val reportingCurrenciesComboBox by fxid<ComboBox<Currency>>()
    private val rememberMeCheckBox by fxid<CheckBox>()
    private val fullscreenCheckBox by fxid<CheckBox>()
    private val hostTextField by fxid<TextField>()
    private val portTextField by fxid<TextField>()
    private val editCancel by fxid<Label>()
    private val save by fxid<Label>()
    private val clientPane by fxid<Node>()

    init {
        reportingCurrenciesComboBox.items = currencies
        reportingCurrenciesComboBox.valueProperty().bindBidirectional(reportingCurrencies)
        rememberMeCheckBox.selectedProperty().bindBidirectional(rememberMe)
        fullscreenCheckBox.selectedProperty().bindBidirectional(fullscreen)
        // TODO : Some host name validations.
        hostTextField.textProperty().bindBidirectional(host)

        portTextField.textFormatter = intFormatter()
        portTextField.textProperty().bindBidirectional(port)

        editCancel.setOnMouseClicked {
            if (!clientPane.isDisable) {
                // Cancel changes and reload properties from disk.
                getModel<SettingsModel>().load()
            }
            clientPane.isDisable = !clientPane.isDisable
        }
        save.setOnMouseClicked {
            getModel<SettingsModel>().commit()
            clientPane.isDisable = true
        }
        save.visibleProperty().bind(clientPane.disableProperty().map { !it })
        editCancel.textProperty().bind(clientPane.disableProperty().map { if (!it) "Cancel" else "Edit" })
        editCancel.graphicProperty().bind(clientPane.disableProperty()
                .map { if (!it) FontAwesomeIconView(FontAwesomeIcon.TIMES) else FontAwesomeIconView(FontAwesomeIcon.EDIT) })
    }
}