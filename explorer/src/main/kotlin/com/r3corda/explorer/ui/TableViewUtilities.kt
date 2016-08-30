package com.r3corda.explorer.ui

import com.r3corda.explorer.formatters.Formatter
import javafx.beans.binding.Bindings
import javafx.beans.value.ObservableValue
import javafx.scene.control.TableCell
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.util.Callback
import org.fxmisc.easybind.EasyBind

fun <S> TableView<S>.setColumnPrefWidthPolicy(
        getColumnWidth: (tableWidthWithoutPaddingAndBorder: Number, column: TableColumn<S, *>) -> Number
) {
    val tableWidthWithoutPaddingAndBorder = Bindings.createDoubleBinding({
        val padding = padding
        val borderInsets = border?.insets
        width -
                (if (padding != null) padding.left + padding.right else 0.0) -
                (if (borderInsets != null) borderInsets.left + borderInsets.right else 0.0)
    }, arrayOf(columns, widthProperty(), paddingProperty(), borderProperty()))

    columns.forEach {
        it.setPrefWidthPolicy(tableWidthWithoutPaddingAndBorder, getColumnWidth)
    }
}

private fun <S> TableColumn<S, *>.setPrefWidthPolicy(
        widthWithoutPaddingAndBorder: ObservableValue<Number>,
        getColumnWidth: (tableWidthWithoutPaddingAndBorder: Number, column: TableColumn<S, *>) -> Number
) {
    prefWidthProperty().bind(EasyBind.map(widthWithoutPaddingAndBorder) {
        getColumnWidth(it, this)
    })
}

fun <S, T> Formatter<T>.toTableCellFactory() = Callback<TableColumn<S, T?>, TableCell<S, T?>> {
    object : TableCell<S, T?>() {
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

