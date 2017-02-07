package net.corda.explorer.views

import com.google.common.net.HostAndPort
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.property.SimpleIntegerProperty
import javafx.scene.control.*
import net.corda.client.fxutils.map
import net.corda.client.model.NodeMonitorModel
import net.corda.client.model.objectProperty
import net.corda.core.exists
import net.corda.explorer.model.SettingsModel
import net.corda.node.services.config.SSLConfiguration
import net.corda.node.services.config.configureTestSSL
import org.controlsfx.dialog.ExceptionDialog
import tornadofx.*
import java.nio.file.Path
import kotlin.system.exitProcess

class LoginView : View() {
    override val root by fxml<DialogPane>()

    private val hostTextField by fxid<TextField>()
    private val portTextField by fxid<TextField>()
    private val usernameTextField by fxid<TextField>()
    private val passwordTextField by fxid<PasswordField>()
    private val rememberMeCheckBox by fxid<CheckBox>()
    private val fullscreenCheckBox by fxid<CheckBox>()
    private val certificateButton by fxid<Button>()
    private val portProperty = SimpleIntegerProperty()

    private val rememberMe by objectProperty(SettingsModel::rememberMeProperty)
    private val username by objectProperty(SettingsModel::usernameProperty)
    private val host by objectProperty(SettingsModel::hostProperty)
    private val port by objectProperty(SettingsModel::portProperty)
    private val fullscreen by objectProperty(SettingsModel::fullscreenProperty)
    private val certificatesDir by objectProperty(SettingsModel::certificatesDirProperty)
    private val keyStorePasswordProperty by objectProperty(SettingsModel::keyStorePasswordProperty)
    private val trustStorePasswordProperty by objectProperty(SettingsModel::trustStorePasswordProperty)

    fun login() {
        val status = Dialog<LoginStatus>().apply {
            dialogPane = root
            setResultConverter {
                when (it?.buttonData) {
                    ButtonBar.ButtonData.OK_DONE -> try {
                        root.isDisable = true
                        // TODO : Run this async to avoid UI lockup.
                        getModel<NodeMonitorModel>().register(HostAndPort.fromParts(hostTextField.text, portProperty.value), configureSSL(), usernameTextField.text, passwordTextField.text)
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

    private fun configureSSL(): SSLConfiguration {
        val sslConfig = object : SSLConfiguration {
            override val certificatesDirectory: Path get() = certificatesDir.get()
            override val keyStorePassword: String get() = keyStorePasswordProperty.get()
            override val trustStorePassword: String get() = trustStorePasswordProperty.get()
        }
        // TODO : Don't use dev certificates.
        return if (sslConfig.keyStoreFile.exists()) sslConfig else configureTestSSL().apply {
            alert(Alert.AlertType.WARNING, "", "KeyStore not found in certificates directory.\nDEV certificates will be used by default.")
        }
    }

    init {
        // Restrict text field to Integer only.
        portTextField.textFormatter = intFormatter().apply { portProperty.bind(this.valueProperty()) }
        rememberMeCheckBox.selectedProperty().bindBidirectional(rememberMe)
        fullscreenCheckBox.selectedProperty().bindBidirectional(fullscreen)
        usernameTextField.textProperty().bindBidirectional(username)
        hostTextField.textProperty().bindBidirectional(host)
        portTextField.textProperty().bindBidirectional(port)
        certificateButton.setOnAction {
            Dialog<ButtonType>().apply {
                title = "Certificates Settings"
                initOwner(root.scene.window)
                dialogPane.content = gridpane {
                    vgap = 10.0
                    hgap = 5.0
                    row("Certificates Directory :") {
                        textfield {
                            prefWidth = 400.0
                            textProperty().bind(certificatesDir.map(Path::toString))
                            isEditable = false
                        }
                        button {
                            graphic = FontAwesomeIconView(FontAwesomeIcon.FOLDER_OPEN_ALT)
                            maxHeight = Double.MAX_VALUE
                            setOnAction {
                                chooseDirectory(owner = dialogPane.scene.window) {
                                    initialDirectoryProperty().bind(certificatesDir.map(Path::toFile))
                                }?.let {
                                    certificatesDir.set(it.toPath())
                                }
                            }
                        }
                    }
                    row("KeyStore Password :") { passwordfield(keyStorePasswordProperty) }
                    row("TrustStore Password :") { passwordfield(trustStorePasswordProperty) }
                }
                dialogPane.buttonTypes.addAll(ButtonType.APPLY, ButtonType.CANCEL)
            }.showAndWait().get().let {
                when (it) {
                    ButtonType.APPLY -> getModel<SettingsModel>().commit()
                // Discard changes.
                    else -> getModel<SettingsModel>().load()
                }
            }
        }
        certificateButton.tooltip("Certificate Configuration")
    }

    private enum class LoginStatus {
        loggedIn, exited, exception
    }
}