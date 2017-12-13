package net.corda.explorer.views.cordapps.iou

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.scene.input.MouseButton
import javafx.scene.layout.BorderPane
import net.corda.core.utilities.Try
import net.corda.explorer.model.CordaView
import net.corda.explorer.model.MembershipListModel
import tornadofx.*

class IOUViewer : CordaView("IOU") {
    // Inject UI elements.
    override val root: BorderPane by fxml()
    override val icon: FontAwesomeIcon = FontAwesomeIcon.CHEVRON_CIRCLE_RIGHT

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
}