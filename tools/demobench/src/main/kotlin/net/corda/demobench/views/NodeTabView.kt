package net.corda.demobench.views

import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import net.corda.demobench.ui.PropertyLabel
import tornadofx.Fragment
import tornadofx.vgrow

class NodeTabView(name: String) : Fragment() {
    override val root by fxml<VBox>()

    val nodeName by fxid<Label>()
    val p2pPort by fxid<PropertyLabel>()
    val states by fxid<PropertyLabel>()
    val transactions by fxid<PropertyLabel>()
    val balance by fxid<PropertyLabel>()

    val viewDatabaseButton by fxid<Button>()
    val launchExplorerButton by fxid<Button>()

    init {
        nodeName.text = name
        root.vgrow = Priority.ALWAYS
    }
}

