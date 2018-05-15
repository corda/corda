package net.corda.demobench.ui

import javafx.scene.control.Label
import javafx.scene.layout.HBox

class PropertyLabel : HBox() {

    val nameLabel = Label()
    val valueLabel = Label()

    var name: String
        get() = nameLabel.text
        set(value) {
            nameLabel.text = value
        }

    var value: String
        get() = valueLabel.text
        set(value) {
            valueLabel.text = value
        }

    init {
        nameLabel.styleClass.add("property-name")
        valueLabel.styleClass.add("property-value")

        children.addAll(nameLabel, valueLabel)
        styleClass.add("property-label")
    }
}
