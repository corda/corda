/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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
class SearchField<T>(private val data: ObservableList<T>, vararg filterCriteria: Pair<String, (T, String) -> Boolean>,
                     val disabledFields: List<String> = emptyList()) : UIComponent() {
    override val root: Parent by fxml()
    private val textField by fxid<TextField>()
    private val clearButton by fxid<Node>()
    private val searchCategory by fxid<ComboBox<String>>()
    private val ALL = "All"

    val filteredData = ChosenList(Bindings.createObjectBinding({
        val text = textField.text
        val category = searchCategory.value
        data.filtered { data ->
            (text.isNullOrBlank() && textField.isVisible) || if (category == ALL) {
                filterCriteria.any { it.second(data, text) }
            } else {
                filterCriteria.toMap()[category]?.invoke(data, text) == true
            }
        }
    }, arrayOf<Observable>(textField.textProperty(), searchCategory.valueProperty(), textField.visibleProperty())))

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
                filterCriteria.map { it.first.toLowerCase() }.joinToString(", ")
            } else {
                it.toLowerCase()
            }
            "Filter by $category."
        })
        textField.visibleProperty().bind(searchCategory.valueProperty().map { it !in disabledFields })
        // TODO Maybe it will be better to replace these categories with comboBox? For example Result with choice: succes, in progress, error.
    }
}