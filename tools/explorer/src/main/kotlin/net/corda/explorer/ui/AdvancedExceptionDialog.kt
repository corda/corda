package net.corda.explorer.ui


import impl.org.controlsfx.i18n.Localization.getString
import impl.org.controlsfx.i18n.Localization.localize
import javafx.event.ActionEvent
import javafx.event.EventHandler
import net.corda.common.logging.errorCodeLocationUrl
import org.controlsfx.dialog.ProgressDialog
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import java.awt.Desktop
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URI

/*
    Will generate a window showing the exception message with a generated link and if requested a stacktrace.
    The link opens the default browser towards the error.corda.com/ redirection pages.
 */
class AdvancedExceptionDialog(_exception: Throwable) : Dialog<ButtonType>() {

    internal val exception = _exception

    init {
        val dialogPane = super.getDialogPane()

        //Dialog title
        super.setTitle(getString("exception.dlg.title"))
        dialogPane.headerText = getString("exception.dlg.header")
        dialogPane.styleClass.add("exception-dialog")
        dialogPane.stylesheets.add(ProgressDialog::class.java.getResource("dialogs.css").toExternalForm())
        dialogPane.buttonTypes.addAll(ButtonType.OK)


        val hyperlink = Hyperlink(exception.errorCodeLocationUrl())
        hyperlink.onAction = EventHandler<ActionEvent> {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse( URI(exception.errorCodeLocationUrl()))
            } //This should be tested out on other platforms, works on my mac but the stackoverflow opinions are mixed.
        }


        val textFlow = TextFlow(Text("${exception.message}\n"), hyperlink)

        dialogPane.content = textFlow
    }
}

//Attach a stacktrace for the exception that was used in the initialization of the dialog.
fun AdvancedExceptionDialog.withStacktrace() : AdvancedExceptionDialog
{
    val sw = StringWriter()
    val pw = PrintWriter(sw)
    exception.printStackTrace(pw)
    val textArea = TextArea(sw.toString()).apply {
        isEditable = false
        isWrapText = false
        maxWidth = Double.MAX_VALUE
        maxHeight = Double.MAX_VALUE
    }

    GridPane.setVgrow(textArea, Priority.ALWAYS)
    GridPane.setHgrow(textArea, Priority.ALWAYS)

    val root = GridPane().apply {
        maxWidth = Double.MAX_VALUE
        add(Label(localize(getString("exception.dlg.label"))), 0, 0)
        add(textArea,0 ,1)
    }

    dialogPane.expandableContent = root
    return this
}