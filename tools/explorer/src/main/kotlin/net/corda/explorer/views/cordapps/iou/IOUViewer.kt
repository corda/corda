/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.explorer.views.cordapps.iou

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.binding.Bindings
import javafx.geometry.Pos
import javafx.scene.input.MouseButton
import javafx.scene.layout.BorderPane
import net.corda.client.jfx.model.TransactionDataModel
import net.corda.client.jfx.model.observableList
import net.corda.client.jfx.utils.map
import net.corda.core.utilities.Try
import net.corda.explorer.model.CordaView
import net.corda.explorer.model.CordaWidget
import net.corda.sample.businessnetwork.iou.IOUState
import net.corda.explorer.model.MembershipListModel
import tornadofx.*

class IOUViewer : CordaView("IOU") {
    // Inject UI elements.
    override val root: BorderPane by fxml()
    override val icon: FontAwesomeIcon = FontAwesomeIcon.CHEVRON_CIRCLE_RIGHT
    override val widgets = listOf(CordaWidget(title, IOUWidget(), icon)).observable()

    // Wire up UI
    init {
        root.top = hbox(5.0) {
            button("New Transaction", FontAwesomeIconView(FontAwesomeIcon.PLUS)) {
                setOnMouseClicked {
                    if (it.button == MouseButton.PRIMARY) {
                        find<NewTransaction>().show(this@IOUViewer.root.scene.window)
                    }
                }
            }
        }
    }

    fun isEnabledForNode(): Boolean = Try.on {
        // Assuming if the model can be initialized - the CorDapp is installed
        val allParties = MembershipListModel().allParties
        allParties[0]
    }.isSuccess

    private class IOUWidget : BorderPane() {
        private val partiallyResolvedTransactions by observableList(TransactionDataModel::partiallyResolvedTransactions)
        private val iouTransactions = partiallyResolvedTransactions.filtered { t -> t.transaction.tx.outputs.any({ ts -> ts.data is IOUState }) }

        init {
            right {
                label {
                    textProperty().bind(Bindings.size(iouTransactions).map(Number::toString))
                    BorderPane.setAlignment(this, Pos.BOTTOM_RIGHT)
                }
            }
        }
    }
}