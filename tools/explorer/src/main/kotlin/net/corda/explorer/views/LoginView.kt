package net.corda.explorer.views

import com.google.common.net.HostAndPort
import javafx.beans.property.SimpleIntegerProperty
import javafx.scene.control.*
import javafx.util.converter.IntegerStringConverter
import org.controlsfx.dialog.ExceptionDialog
import tornadofx.View
import java.util.regex.Pattern
import kotlin.system.exitProcess

class LoginView : View() {
    override val root: DialogPane by fxml()

    private val host by fxid<TextField>()
    private val port by fxid<TextField>()
    private val username by fxid<TextField>()
    private val password by fxid<PasswordField>()
    private val portProperty = SimpleIntegerProperty()

    fun login(loginFunction: (HostAndPort, String, String) -> Unit) {
        val loggedIn = Dialog<Boolean>().apply {
            dialogPane = root
            var exception = false
            setResultConverter {
                exception = false
                when (it?.buttonData) {
                    ButtonBar.ButtonData.OK_DONE -> try {
                        // TODO : Run this async to avoid UI lockup.
                        loginFunction(HostAndPort.fromParts(host.text, portProperty.value), username.text, password.text)
                        true
                    } catch (e: Exception) {
                        ExceptionDialog(e).showAndWait()
                        exception = true
                        false
                    }
                    else -> false
                }
            }
            setOnCloseRequest {
                if (!result && !exception) {
                    when (Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to exit Corda explorer?").apply {
                    }.showAndWait().get()) {
                        ButtonType.OK -> exitProcess(0)
                    }
                }
            }
        }.showAndWait().get()

        if (!loggedIn) login(loginFunction)
    }

    init {
        // Restrict text field to Integer only.
        val integerFormat = Pattern.compile("-?(\\d*)").run {
            TextFormatter<Int>(IntegerStringConverter(), null) { change ->
                val newText = change.controlNewText
                if (matcher(newText).matches()) change else null
            }
        }
        port.textFormatter = integerFormat
        portProperty.bind(integerFormat.valueProperty())
    }
}
