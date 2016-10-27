package com.r3corda.explorer.model

import javafx.beans.property.SimpleObjectProperty

enum class SelectedView {
    Home,
    Cash,
    Transaction,
    NewTransaction
}

class TopLevelModel {
    val selectedView = SimpleObjectProperty<SelectedView>(SelectedView.Home)
}
