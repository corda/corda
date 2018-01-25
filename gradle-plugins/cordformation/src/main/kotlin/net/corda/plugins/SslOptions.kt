package net.corda.plugins

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

class SslOptions {
    private var config: Config = ConfigFactory.empty()

    /**
     * Password for the keystore.
     */
    fun keyStorePassword(value: String) {
        config += "keyStorePassword" to value
    }

    /**
     * Password for the truststore.
     */
    fun trustStorePassword(value: String) {
        config += "trustStorePassword" to value
    }

    /**
     * Directory under which key stores are to be placed.
     */
    fun certificatesDirectory(value: String) {
        config += "certificatesDirectory" to value
    }

    /**
     * Absolute path to SSL keystore. Default: "[certificatesDirectory]/sslkeystore.jks"
     */
    fun sslKeystore(value: String) {
        config += "sslKeystore" to value
    }

    /**
     * Absolute path to SSL truststore. Default: "[certificatesDirectory]/truststore.jks"
     */
    fun trustStoreFile(value: String) {
        config += "trustStoreFile" to value
    }

    internal fun addTo(key: String, config: Config): Config {
        if (this.config.isEmpty) {
            return config
        }
        return config + (key to this.config.root())
    }
}