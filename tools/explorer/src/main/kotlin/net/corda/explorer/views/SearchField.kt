package net.corda.explorer.views

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.Observable
import javafx.beans.binding.Bindings
import javafx.collections.ObservableList
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.ComboBox
import javafx.scene.control.ListCell
import javafx.scene.control.TextField
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import net.corda.client.jfx.utils.ChosenList
import net.corda.client.jfx.utils.map
import tornadofx.*

/**
 * Generic search bar filters [ObservableList] with provided filterCriteria.
 * TODO : Predictive text?
 * TODO : Regex?
 */
class SearchField<T>(private val data: ObservableList<T>, vararg filterCriteria: Pair<String, (T, String) -> Boolean>) : UIComponent() {
    override val root: Parent by fxml()
    private val textField by fxid<TextField>()
    private val clearButton by fxid<Node>()
    private val searchCategory by fxid<ComboBox<String>>()
    private val ALL = "All"

    val filteredData = ChosenList(Bindings.createObjectBinding({
        val text = textField.text
        val category = searchCategory.value
        data.filtered { data ->
            text.isNullOrBlank() || if (category == ALL) {
                filterCriteria.any { it.second(data, text) }
            } else {
                filterCriteria.toMap()[category]?.invoke(data, text) == true
            }
        }
    }, arrayOf<Observable>(textField.textProperty(), searchCategory.valueProperty())), "filteredData")

    init {
        clearButton.setOnMouseClicked { event: MouseEvent ->
            if (event.button == MouseButton.PRIMARY) {
                textField.clear()
            }
        }
        searchCategory.items = filterCriteria.map { it.first }.observable()
        searchCategory.items.add(0, ALL)
        searchCategory.value = ALL

        val search = FontAwesomeIconView(FontAwesomeIcon.SEARCH)
        searchCategory.buttonCell = object : ListCell<String>() {
            override fun updateItem(item: String?, empty: Boolean) {
                super.updateItem(item, empty)
                setText(item)
                setGraphic(search)
                setAlignment(Pos.CENTER)
            }
        }
        // TODO : find a way to replace these magic numbers.
        textField.paddingProperty().bind(searchCategory.widthProperty().map {
            Insets(5.0, 5.0, 5.0, it.toDouble() + 10)
        })
        textField.promptTextProperty().bind(searchCategory.valueProperty().map {
            val category = if (it == ALL) {
                filterCriteria.joinToString(", ") { it.first.toLowerCase() }
            } else {
                it.toLowerCase()
            }
            "Filter by $category."
        })
    }
}