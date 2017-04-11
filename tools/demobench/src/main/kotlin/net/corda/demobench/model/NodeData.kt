package net.corda.demobench.model

import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleListProperty
import javafx.beans.property.SimpleStringProperty
import tornadofx.*

class NodeData {

    val legalName = SimpleStringProperty("")
    val nearestCity = SimpleStringProperty("London")
    val p2pPort = SimpleIntegerProperty()
    val rpcPort = SimpleIntegerProperty()
    val webPort = SimpleIntegerProperty()
    val h2Port = SimpleIntegerProperty()
    val extraServices = SimpleListProperty(mutableListOf<String>().observable())

}
