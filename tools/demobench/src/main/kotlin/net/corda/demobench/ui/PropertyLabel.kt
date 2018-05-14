/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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
