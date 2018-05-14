package net.corda.explorer.ui

import javafx.beans.binding.Bindings
import javafx.beans.binding.ObjectBinding
import javafx.beans.value.ObservableValue
import javafx.scene.Node
import javafx.scene.control.TableCell
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.util.Callback
import net.corda.explorer.formatters.Formatter
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

fun <S> TableView<S>.singleRowSelection(): ObjectBinding<SingleRowSelection<S>> = Bindings.createObjectBinding({
    if (selectionModel.selectedItems.size == 0) {
        SingleRowSelection.None<S>()
    } else {
        SingleRowSelection.Selected(selectionModel.selectedItems[0])
    }
}, arrayOf(selectionModel.selectedItems))

fun <S, T> TableColumn<S, T>.setCustomCellFactory(toNode: (T) -> Node) {
    setCellFactory {
        object : TableCell<S, T>() {
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
