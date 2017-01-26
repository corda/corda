package net.corda.demobench.model

import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty

class NodeData {

    var legalName = SimpleStringProperty("")
    val nearestCity = SimpleStringProperty("London")
    var p2pPort = SimpleIntegerProperty(0)
    val artemisPort = SimpleIntegerProperty(0)
    val webPort = SimpleIntegerProperty(0)

}