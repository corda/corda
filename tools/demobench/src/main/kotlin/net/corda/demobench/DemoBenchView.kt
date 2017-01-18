package net.corda.demobench

import javafx.scene.Parent
import tornadofx.View
import tornadofx.importStylesheet

class DemoBenchView : View("Corda Demo Bench") {
    override val root: Parent by fxml()

    init {
        importStylesheet("/net/corda/demobench/style.css")
    }
}
