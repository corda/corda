package net.corda.explorer.views

import com.google.common.net.HostAndPort
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.property.SimpleIntegerProperty
import javafx.scene.control.*
import javafx.stage.FileChooser
import net.corda.client.fxutils.map
import net.corda.client.model.NodeMonitorModel
import net.corda.client.model.objectProperty
import net.corda.explorer.model.SettingsModel
import net.corda.node.services.config.SSLConfiguration
import org.controlsfx.dialog.ExceptionDialog
import tornadofx.*
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

class LoginView : View() {
    override val root by fxml<DialogPane>()

    private val hostTextField by fxid<TextField>()
    private val portTextField by fxid<TextField>()
    private val usernameTextField by fxid<TextField>()
    private val passwordTextField by fxid<PasswordField>()
    private val rememberMeCheckBox by fxid<CheckBox>()
    private val fullscreenCheckBox by fxid<CheckBox>()
    private val portProperty = SimpleIntegerProperty()

    private val rememberMe by objectProperty(SettingsModel::rememberMeProperty)
    private val username by objectProperty(SettingsModel::usernameProperty)
    private val host by objectProperty(SettingsModel::hostProperty)
    private val port by objectProperty(SettingsModel::portProperty)
    private val fullscreen by objectProperty(SettingsModel::fullscreenProperty)

    fun login() {
        val status = Dialog<LoginStatus>().apply {
            dialogPane = root
            setResultConverter {
                when (it?.buttonData) {
                    ButtonBar.ButtonData.OK_DONE -> try {
                        root.isDisable = true
                        // TODO : Run this async to avoid UI lockup.
                        getModel<NodeMonitorModel>().register(HostAndPort.fromParts(hostTextField.text, portProperty.value), usernameTextField.text, passwordTextField.text)
                        if (!rememberMe.value) {
                            username.value = ""
                            host.value = ""
                            port.value = ""
                        }
                        getModel<SettingsModel>().commit()
                        LoginStatus.loggedIn
                    } catch (e: Exception) {
                        // TODO : Handle this in a more user friendly way.
                        e.printStackTrace()
                        ExceptionDialog(e).apply { initOwner(root.scene.window) }.showAndWait()
                        LoginStatus.exception
                    } finally {
                        root.isDisable = false
                    }
                    else -> LoginStatus.exited
                }
            }
            setOnCloseRequest {
                if (result == LoginStatus.exited) {
                    val button = Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to exit Corda Explorer?").apply {
                        initOwner(root.scene.window)
                    }.showAndWait().get()
                    if (button == ButtonType.OK) {
                        exitProcess(0)
                    }
                }
            }
        }.showAndWait().get()
        if (status != LoginStatus.loggedIn) login()
    }

    init {
        // Restrict text field to Integer only.
        portTextField.textFormatter = intFormatter().apply { portProperty.bind(this.valueProperty()) }
        rememberMeCheckBox.selectedProperty().bindBidirectional(rememberMe)
        fullscreenCheckBox.selectedProperty().bindBidirectional(fullscreen)
        usernameTextField.textProperty().bindBidirectional(username)
        hostTextField.textProperty().bindBidirectional(host)
        portTextField.textProperty().bindBidirectional(port)
    }

    private enum class LoginStatus {
        loggedIn, exited, exception
    }
}