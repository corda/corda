package com.r3corda.explorer.views

import com.r3corda.client.model.observableValue
import com.r3corda.explorer.model.SelectedView
import com.r3corda.explorer.model.TopLevelModel
import javafx.beans.value.ObservableValue
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.VBox
import org.fxmisc.easybind.EasyBind
import tornadofx.View

class Header : View() {
    override val root: VBox by fxml()

    private val sectionIcon: ImageView by fxid("SectionIcon")
    private val sectionIconContainer: VBox by fxid("SectionIconContainer")
    private val sectionLabel: Label by fxid("SectionLabel")
    private val debugNextButton: Button by fxid("DebugNextButton")
    private val debugGoStopButton: Button by fxid("DebugGoStopButton")

    private val selectedView: ObservableValue<SelectedView> by observableValue(TopLevelModel::selectedView)

    private val homeImage = Image("/com/r3corda/explorer/images/home.png")
    private val cashImage = Image("/com/r3corda/explorer/images/cash.png")
    private val transactionImage = Image("/com/r3corda/explorer/images/tx.png")

    init {
        sectionLabel.textProperty().bind(EasyBind.map(selectedView) {
            when (it) {
                SelectedView.Home -> "Home"
                SelectedView.Cash -> "Cash"
                SelectedView.Transaction -> "Transaction"
                null -> "Home"
            }
        })

        sectionIcon.imageProperty().bind(EasyBind.map(selectedView) {
            when (it) {
                SelectedView.Home -> homeImage
                SelectedView.Cash -> cashImage
                SelectedView.Transaction -> transactionImage
                null -> homeImage
            }
        })

        // JavaFX bugs and doesn't invalidate the wrapping Box's height if the icon fit height is first set to
        // unbounded (0.0) - which is what the label's height is initially, so we set it to 1.0 instead
        val secionLabelHeightNonZero = EasyBind.map(sectionLabel.heightProperty()) {
            if (it == 0.0) {
                1.0
            } else {
                it.toDouble()
            }
        }

        sectionIconContainer.minWidthProperty().bind(secionLabelHeightNonZero)
        sectionIcon.fitWidthProperty().bind(secionLabelHeightNonZero)
        sectionIcon.fitHeightProperty().bind(sectionIcon.fitWidthProperty())
    }
}
