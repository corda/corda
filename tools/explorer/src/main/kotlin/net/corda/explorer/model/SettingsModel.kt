/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.explorer.model

import javafx.beans.InvalidationListener
import javafx.beans.Observable
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import net.corda.core.internal.*
import tornadofx.*
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

    // Create observable Properties.
    val reportingCurrencyProperty = writableConfigProperty(SettingsModel::reportingCurrency)
    val rememberMeProperty = writableConfigProperty(SettingsModel::rememberMe)
    val hostProperty = writableConfigProperty(SettingsModel::host)
    val portProperty = writableConfigProperty(SettingsModel::port)
    val usernameProperty = writableConfigProperty(SettingsModel::username)
    val fullscreenProperty = writableConfigProperty(SettingsModel::fullscreen)

    init {
        load()
    }

    // Load config from properties file.
    fun load() = config.apply {
        clear()
        if (path.exists()) path.read { load(it) }
        listeners.forEach { it.invalidated(this@SettingsModel) }
    }

    // Save all changes in memory to properties file.
    fun commit() = path.write { config.store(it, "") }

    private operator fun <T> Properties.getValue(receiver: Any, metadata: KProperty<*>): T {
        return when (metadata.returnType.javaType) {
            String::class.java -> uncheckedCast(string(metadata.name, ""))
            Int::class.java -> uncheckedCast(string(metadata.name, "0").toInt())
            Boolean::class.java -> uncheckedCast(boolean(metadata.name))
            Currency::class.java -> uncheckedCast(Currency.getInstance(string(metadata.name, "USD")))
            Path::class.java -> uncheckedCast(Paths.get(string(metadata.name, "")).toAbsolutePath())
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