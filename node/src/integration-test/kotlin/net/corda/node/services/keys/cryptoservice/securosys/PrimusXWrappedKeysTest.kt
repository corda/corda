package net.corda.node.services.keys.cryptoservice.securosys

import com.securosys.primus.jce.PrimusProvider
import com.typesafe.config.ConfigFactory
import net.corda.core.internal.toPath
import net.corda.node.services.keys.cryptoservice.AbstractWrappedKeysTest
import net.corda.node.services.keys.cryptoservice.aliceName
import net.corda.node.services.keys.cryptoservice.genevieveName
import net.corda.node.services.keys.cryptoservice.notaryName
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.cryptoservice.SupportedCryptoServices
import net.corda.nodeapi.internal.cryptoservice.securosys.PrimusXCryptoService
import net.corda.testing.internal.IntegrationTestSchemas
import org.junit.ClassRule
import org.junit.Ignore
import java.nio.file.Path
import java.security.KeyStore

@Ignore
class PrimusXWrappedKeysTest: AbstractWrappedKeysTest() {

    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(notaryName, aliceName, genevieveName)
    }

    override fun configPath(): Path = javaClass.getResource("primusx.conf").toPath()

    override fun cryptoServiceName(): String = SupportedCryptoServices.PRIMUS_X.name

    override fun mode(): String = "WRAPPED"

    override fun deleteEntries(aliases: List<String>) {
        val cryptoService = getCryptoService()
        aliases.forEach {
            cryptoService.withAuthentication {
                if (cryptoService.containsKey(it))
                    cryptoService.delete(it)
            }
        }
    }

    private fun getCryptoService(): PrimusXCryptoService {
        val config = ConfigFactory.parseFile(configPath().toFile()).resolve().parseAs(PrimusXCryptoService.Companion.PrimusXConfiguration::class)

        val provider = PrimusProvider()
        val keyStore = KeyStore.getInstance(PrimusProvider.getKeyStoreTypeName(), provider)
        return PrimusXCryptoService(keyStore, provider, auth = { config })
    }

}