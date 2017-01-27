package net.corda.demobench.model

import tornadofx.observable
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleListProperty
import javafx.beans.property.SimpleStringProperty

class NodeData {

    val legalName = SimpleStringProperty("")
    val nearestCity = SimpleStringProperty("London")
    val artemisPort = SimpleIntegerProperty(0)
    val webPort = SimpleIntegerProperty(0)
    val extraServices = SimpleListProperty(mutableListOf<String>().observable())

}
