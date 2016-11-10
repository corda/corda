package net.corda.explorer.model

import javafx.beans.property.SimpleObjectProperty
import javafx.scene.image.Image

enum class SelectedView(val displayableName: String, val image: Image, val subviews: Array<SelectedView> = emptyArray()) {
    Home("Home", getImage("home.png")),
    Transaction("Transaction", getImage("tx.png")),
    Setting("Setting", getImage("settings_lrg.png")),
    NewTransaction("New Transaction", getImage("cash.png")),
    Cash("Cash", getImage("cash.png"), arrayOf(Transaction, NewTransaction)),
    NetworkMap("Network Map", getImage("cash.png")),
    Vault("Vault", getImage("cash.png"), arrayOf(Cash)),
    Network("Network", getImage("inst.png"), arrayOf(NetworkMap, Transaction))
}

private fun getImage(imageName: String): Image {
    val basePath = "/net/corda/explorer/images"
    return Image("$basePath/$imageName")
}

class TopLevelModel {
    val selectedView = SimpleObjectProperty<SelectedView>(SelectedView.Home)
}
