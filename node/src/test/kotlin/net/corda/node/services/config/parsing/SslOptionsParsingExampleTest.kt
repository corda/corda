package net.corda.node.services.config.parsing

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

class SslOptionsParsingExampleTest {

    @Test
    fun manual_parsing() {

        val expectedKeyStore = SslOptions.CertificateStore(Password("dadada"), Paths.get("./certs/keystore"))
        val expectedTrustStore = SslOptions.CertificateStore(Password("hehehe"), Paths.get("./certs/truststore"))

        val config = configObject("keystore" to configObject("password" to expectedKeyStore.password.value, "path" to expectedKeyStore.path.toString()), "trustStore" to configObject("password" to expectedTrustStore.password.value, "path" to expectedTrustStore.path.toString())).toConfig()

        val keyStoreProperty = certificateStore("keystore")
        val trustStoreProperty = certificateStore("trustStore")

        val keyStore = keyStoreProperty.valueIn(config)
        val trustStore = trustStoreProperty.valueIn(config)

        val sslOptions = SslOptionsImpl(keyStore, trustStore)

        assertThat(sslOptions.keyStore).isEqualTo(expectedKeyStore)
        assertThat(sslOptions.trustStore).isEqualTo(expectedTrustStore)
    }
}

private fun sslOptionsSchema(strict: Boolean) = ConfigSchema.withProperties(strict) { setOf(certificateStore("keyStore"), certificateStore("trustStore")) }

// TODO sollecitom think about how to allow custom validation for mapping functions
private fun certificateStoreConfigSchema(strict: Boolean) = ConfigSchema.withProperties(strict) { setOf(password("password"), path("path")) }

private fun certificateStore(key: String): ConfigProperty<SslOptions.CertificateStore> = ConfigProperty.value(key).map { obj -> obj.toConfig() }.map { obj -> SslOptions.CertificateStore(password("password").valueIn(obj), path("path").valueIn(obj)) }

private fun password(key: String): ConfigProperty<Password> = ConfigProperty.string(key).map(Password::class.java.simpleName) { value -> Password(value) }

private fun path(key: String): ConfigProperty<Path> = ConfigProperty.string(key).map(Path::class.java.simpleName) { value -> Paths.get(value) }

private interface SslOptions {

    val keyStore: CertificateStore
    val trustStore: CertificateStore

    data class CertificateStore(val password: Password, val path: Path)
}

private data class Password(val value: String) {

    init {
        require(value.length >= 6) { "Passwords require minimum 6 characters" }
    }
}

private data class SslOptionsImpl(override val keyStore: SslOptions.CertificateStore, override val trustStore: SslOptions.CertificateStore) : SslOptions
