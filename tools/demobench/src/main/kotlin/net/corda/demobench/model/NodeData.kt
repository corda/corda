package net.corda.demobench.model

import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty

class NodeData {

    var legalName : SimpleStringProperty = SimpleStringProperty("")
    var p2pPort: SimpleIntegerProperty = SimpleIntegerProperty(0)
    val artemisPort: SimpleIntegerProperty = SimpleIntegerProperty(0)
    val webPort: SimpleIntegerProperty = SimpleIntegerProperty(0)

}