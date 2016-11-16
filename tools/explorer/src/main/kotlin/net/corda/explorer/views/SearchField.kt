package net.corda.explorer.views

import javafx.collections.ObservableList
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.TextField
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import net.corda.client.fxutils.ChosenList
import net.corda.client.fxutils.filter
import net.corda.client.fxutils.lift
import net.corda.client.fxutils.map
import tornadofx.UIComponent
import tornadofx.observable

class SearchField<T>(private val data: ObservableList<T>, vararg filterCriteria: (T, String) -> Boolean) : UIComponent() {

    override val root: Parent by fxml()
    private val textField by fxid<TextField>()
    private val clearButton by fxid<Node>()

    // Currently this method apply each filter to the collection and return the collection with most matches.
    // TODO : Allow user to chose if there are matches in multiple category.
    val filteredData = ChosenList(textField.textProperty().map { text ->
        if (text.isBlank()) data else filterCriteria.map { criterion ->
            data.filter({ state: T -> criterion(state, text) }.lift())
        }.maxBy { it.size } ?: emptyList<T>().observable()
    })

    init {
        clearButton.setOnMouseClicked { event: MouseEvent ->
            if (event.button == MouseButton.PRIMARY) {
                textField.clear()
            }
        }
    }
}