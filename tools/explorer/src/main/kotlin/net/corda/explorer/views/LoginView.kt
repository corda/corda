package net.corda.explorer.views

import com.google.common.net.HostAndPort
import javafx.beans.property.SimpleIntegerProperty
import javafx.scene.control.*
import org.controlsfx.dialog.ExceptionDialog
import tornadofx.View
import kotlin.system.exitProcess

class LoginView : View() {
    override val root by fxml<DialogPane>()

    private val host by fxid<TextField>()
    private val port by fxid<TextField>()
    private val username by fxid<TextField>()
    private val password by fxid<PasswordField>()
    private val portProperty = SimpleIntegerProperty()

    fun login(loginFunction: (HostAndPort, String, String) -> Unit) {
        val status = Dialog<LoginStatus>().apply {
            dialogPane = root
            setResultConverter {
                when (it?.buttonData) {
                    ButtonBar.ButtonData.OK_DONE -> try {
                        // TODO : Run this async to avoid UI lockup.
                        loginFunction(HostAndPort.fromParts(host.text, portProperty.value), username.text, password.text)
                        LoginStatus.loggedIn
                    } catch (e: Exception) {
                        // TODO : Handle this in a more user friendly way.
                        ExceptionDialog(e).apply { initOwner(root.scene.window) }.showAndWait()
                        LoginStatus.exception
                    }
                    else -> LoginStatus.exited
                }
            }
            setOnCloseRequest {
                if (result == LoginStatus.exited) {
                    val button = Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to exit Corda explorer?").apply {
                        initOwner(root.scene.window)
                    }.showAndWait().get()
                    if (button == ButtonType.OK) {
                        exitProcess(0)
                    }
                }
            }
        }.showAndWait().get()
        if (status != LoginStatus.loggedIn) login(loginFunction)
    }

    init {
        // Restrict text field to Integer only.
        port.textFormatter = intFormatter().apply { portProperty.bind(this.valueProperty()) }
    }

    private enum class LoginStatus {
        loggedIn, exited, exception
    }
}