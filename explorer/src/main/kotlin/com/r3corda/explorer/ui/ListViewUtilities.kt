package com.r3corda.explorer.ui

import com.r3corda.explorer.formatters.Formatter
import javafx.scene.Node
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.util.Callback

fun <T> Formatter<T>.toListCellFactory() = Callback<ListView<T?>, ListCell<T?>> {
    object : ListCell<T?>() {
        override fun updateItem(value: T?, empty: Boolean) {
            super.updateItem(value, empty)
            text = if (value == null || empty) {
                ""
            } else {
                format(value)
            }
        }
    }
}

fun <T> ListView<T>.setCustomCellFactory(toNode: (T) -> Node) {
    setCellFactory {
        object : ListCell<T>() {
            init {
                text = null
            }
            override fun updateItem(value: T?, empty: Boolean) {
                super.updateItem(value, empty)
                graphic = if (value != null && !empty) {
                    toNode(value)
                } else {
                    null
                }
            }
        }
    }
}
