package com.r3corda.explorer.components

import javafx.scene.control.Alert
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import java.io.PrintWriter
import java.io.StringWriter

class ExceptionDialog(ex: Throwable) : Alert(AlertType.ERROR) {

    private fun Throwable.toExceptionText(): String {
        return StringWriter().use {
            PrintWriter(it).use {
                this.printStackTrace(it)
            }
            it.toString()
        }
    }

    init {
        // Create expandable Exception.
        val label = Label("The exception stacktrace was:")
        contentText = ex.message

        val textArea = TextArea(ex.toExceptionText())
        textArea.isEditable = false
        textArea.isWrapText = true

        textArea.maxWidth = Double.MAX_VALUE
        textArea.maxHeight = Double.MAX_VALUE
        GridPane.setVgrow(textArea, Priority.ALWAYS)
        GridPane.setHgrow(textArea, Priority.ALWAYS)

        val expContent = GridPane()
        expContent.maxWidth = Double.MAX_VALUE
        expContent.add(label, 0, 0)
        expContent.add(textArea, 0, 1)

        // Set expandable Exception into the dialog pane.
        dialogPane.expandableContent = expContent
    }
}
