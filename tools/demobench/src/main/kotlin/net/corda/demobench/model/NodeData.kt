package net.corda.demobench.model

import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty

class NodeData {

    var legalName = SimpleStringProperty("")
    val nearestCity = SimpleStringProperty("London")
    val artemisPort = SimpleIntegerProperty(0)
    val webPort = SimpleIntegerProperty(0)

}
