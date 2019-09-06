package net.corda.node.services.keys.cryptoservice.futurex

import com.typesafe.config.ConfigFactory
import fx.security.pkcs11.SunPKCS11
import net.corda.core.internal.toPath
import net.corda.node.services.keys.cryptoservice.AbstractNodeRegistrationTest
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.futurex.FutureXCryptoService
import org.junit.Ignore
import java.nio.file.Path
import java.security.KeyStore
import javax.security.auth.callback.PasswordCallback
import javax.security.auth.x500.X500Principal

@Ignore
class FutureXNodeRegistrationTest : AbstractNodeRegistrationTest() {
    override fun cryptoServiceName(): String {
        return "FUTUREX"
    }

    override val systemProperties = mapOf(
            "java.library.path" to System.getProperty("java.library.path"))

    override fun configPath(): Path {
        return javaClass.getResource("futurex.conf").toPath()
    }

    override fun getCryptoService(x500Principal: X500Principal, config: Path): CryptoService {
        return FutureXCryptoService.fromConfigurationFile(aliceName.x500Principal, configPath())
    }

    override fun deleteExistingEntries() {
        val config = ConfigFactory.parseFile(configPath().toFile()).resolve().parseAs(FutureXCryptoService.FutureXConfiguration::class)
        val provider = SunPKCS11()
        provider.login(null) { callbacks -> (callbacks[0] as PasswordCallback).password = config.credentials.toCharArray() }
        val keyStore: KeyStore = KeyStore.getInstance(FutureXCryptoService.KEYSTORE_TYPE, provider)
        keyStore.load(null, null)
        keyStore.deleteEntry("identity-private-key")
        keyStore.deleteEntry("cordaclientca")
    }
}