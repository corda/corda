package net.corda.explorer.model

import javafx.beans.InvalidationListener
import javafx.beans.Observable
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import net.corda.core.contracts.currency
import net.corda.core.createDirectories
import net.corda.core.div
import net.corda.core.exists
import tornadofx.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaType

class SettingsModel(path: Path = Paths.get("conf")) : Component(), Observable {
    // Using CordaExplorer as config file name instead of TornadoFX default.
    private val path = path.apply { if (!exists()) createDirectories() } / "CordaExplorer.properties"

    private val listeners = mutableListOf<InvalidationListener>()

    // Delegate to config.
    private var rememberMe: Boolean by config
    private var host: String by config
    private var port: String by config
    private var username: String by config
    private var reportingCurrency: Currency by config
    private var fullscreen: Boolean by config
    private var certificatesDir: Path by config
    private var keyStorePassword: String by config
    private var trustStorePassword: String by config

    // Create observable Properties.
    val reportingCurrencyProperty = writableConfigProperty(SettingsModel::reportingCurrency)
    val rememberMeProperty = writableConfigProperty(SettingsModel::rememberMe)
    val hostProperty = writableConfigProperty(SettingsModel::host)
    val portProperty = writableConfigProperty(SettingsModel::port)
    val usernameProperty = writableConfigProperty(SettingsModel::username)
    val fullscreenProperty = writableConfigProperty(SettingsModel::fullscreen)
    val certificatesDirProperty = writableConfigProperty(SettingsModel::certificatesDir)
    // TODO : We should encrypt all passwords in config file.
    val keyStorePasswordProperty = writableConfigProperty(SettingsModel::keyStorePassword)
    val trustStorePasswordProperty = writableConfigProperty(SettingsModel::trustStorePassword)

    init {
        load()
    }

    // Load config from properties file.
    fun load() = config.apply {
        clear()
        if (Files.exists(path)) Files.newInputStream(path).use { load(it) }
        listeners.forEach { it.invalidated(this@SettingsModel) }
    }

    // Save all changes in memory to properties file.
    fun commit() = Files.newOutputStream(path).use { config.store(it, "") }

    @Suppress("UNCHECKED_CAST")
    private operator fun <T> Properties.getValue(receiver: Any, metadata: KProperty<*>): T {
        return when (metadata.returnType.javaType) {
            String::class.java -> string(metadata.name, "") as T
            Int::class.java -> string(metadata.name, "0").toInt() as T
            Boolean::class.java -> boolean(metadata.name) as T
            Currency::class.java -> currency(string(metadata.name, "USD")) as T
            Path::class.java -> Paths.get(string(metadata.name, "")).toAbsolutePath() as T
            else -> throw IllegalArgumentException("Unsupported type ${metadata.returnType}")
        }
    }

    private operator fun <T> Properties.setValue(receiver: Any, metadata: KProperty<*>, value: T) {
        set(metadata.name to value)
    }

    // Observable implementation for notifying properties when config reloaded.
    override fun removeListener(listener: InvalidationListener?) {
        listener?.let { listeners.remove(it) }
    }

    override fun addListener(listener: InvalidationListener?) {
        listener?.let { listeners.add(it) }
    }

    // Writable Object Property which write through to delegated property.
    private fun <S : Observable, T> S.writableConfigProperty(k: KMutableProperty1<S, T>): ObjectProperty<T> {
        val s = this
        return object : SimpleObjectProperty<T>(k.get(this)) {
            init {
                // Add listener to reset value when config reloaded.
                s.addListener { value = k.get(s) }
            }

            override fun set(newValue: T) {
                super.set(newValue)
                k.set(s, newValue)
            }
        }
    }
}