package net.corda.node.services.keys.cryptoservice.securosys

import com.securosys.primus.jce.PrimusProvider
import com.typesafe.config.ConfigFactory
import net.corda.core.internal.toPath
import net.corda.node.services.keys.cryptoservice.AbstractNodeRegistrationTest
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.SupportedCryptoServices
import net.corda.nodeapi.internal.cryptoservice.securosys.PrimusXCryptoService
import org.junit.Ignore
import java.nio.file.Path
import java.security.KeyStore
import javax.security.auth.x500.X500Principal

/**
 * This test can run against the cloud HSM provided by Securosys.
 * In order to run it, one has to fill in the appropriate values in the primusx.conf file under resources.
 */
@Ignore
class PrimusXNodeRegistrationTest: AbstractNodeRegistrationTest() {

    override fun cryptoServiceName(): String = SupportedCryptoServices.PRIMUS_X.name

    override fun configPath(): Path = javaClass.getResource("primusx.conf").toPath()

    override fun getCryptoService(x500Principal: X500Principal, config: Path): CryptoService {
        val config = ConfigFactory.parseFile(configPath().toFile()).resolve().parseAs(PrimusXCryptoService.Companion.PrimusXConfiguration::class)

        val provider = PrimusProvider()
        val keyStore = KeyStore.getInstance(PrimusProvider.getKeyStoreTypeName(), provider)
        return PrimusXCryptoService(keyStore, provider, x500Principal, auth = { config })
    }

    override fun deleteExistingEntries() {
        val config = ConfigFactory.parseFile(configPath().toFile()).resolve().parseAs(PrimusXCryptoService.Companion.PrimusXConfiguration::class)
        val provider = PrimusProvider()
        val keyStore = KeyStore.getInstance(PrimusProvider.getKeyStoreTypeName(), provider)
        val securosysCryptoService = PrimusXCryptoService(keyStore, PrimusProvider(), auth = { config })
        securosysCryptoService.withAuthentication {
            if (keyStore.containsAlias("identity-private-key"))
                keyStore.deleteEntry("identity-private-key")
        }
    }

}