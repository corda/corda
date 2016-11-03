package com.r3corda.explorer.views

import com.r3corda.client.fxutils.map
import com.r3corda.client.model.NetworkIdentityModel
import com.r3corda.client.model.observableValue
import com.r3corda.explorer.model.TopLevelModel
import javafx.scene.control.Label
import javafx.scene.control.SplitMenuButton
import javafx.scene.image.ImageView
import javafx.scene.layout.GridPane
import tornadofx.View

class Header : View() {
    override val root: GridPane by fxml()

    private val sectionLabel: Label by fxid()
    private val userButton: SplitMenuButton by fxid()
    private val myIdentity by observableValue(NetworkIdentityModel::myIdentity)
    private val selectedView by observableValue(TopLevelModel::selectedView)

    init {
        sectionLabel.textProperty().bind(selectedView.map { it.displayableName })
        sectionLabel.graphicProperty().bind(selectedView.map {
            ImageView(it.image).apply {
                fitHeight = 30.0
                fitWidth = 30.0
            }
        })
        userButton.textProperty().bind(myIdentity.map { it?.legalIdentity?.name })
    }
}