package net.corda.demobench.ui

import javafx.scene.control.ContentDisplay
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox

class PropertyLabel : HBox() {
    private val nameLabel = Label()
    private val myTooltip = Tooltip()

    private var nameText = ""
    private var valueText = ""

    var name: String
        get() = nameText
        set(value) {
            nameText = value
            updateText()
        }

    var value: String
        get() = valueText
        set(value) {
            valueText = value
            updateText()
        }

    private fun updateText() {
        nameLabel.text = "$nameText $valueText"
        myTooltip.text = "$nameText $valueText"
    }

    init {
        nameLabel.styleClass.add("property-name")
        myTooltip.contentDisplay = ContentDisplay.CENTER
        Tooltip.install(nameLabel, myTooltip)
        children.addAll(nameLabel)
        styleClass.add("property-label")
    }
}
