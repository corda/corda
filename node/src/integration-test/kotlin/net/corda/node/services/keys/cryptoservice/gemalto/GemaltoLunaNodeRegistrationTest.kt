package net.corda.node.services.keys.cryptoservice.gemalto

import com.safenetinc.luna.provider.LunaProvider
import com.typesafe.config.ConfigFactory
import net.corda.core.internal.toPath
import net.corda.node.services.keys.cryptoservice.AbstractNodeRegistrationTest
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.SupportedCryptoServices
import net.corda.nodeapi.internal.cryptoservice.gemalto.GemaltoLunaCryptoService
import org.junit.Ignore
import java.nio.file.Path
import java.security.KeyStore
import javax.security.auth.x500.X500Principal

/*
 * See the comment on [GemaltoLunaCryptoServiceTest] as to why this test is not enabled.
 */
@Ignore
class GemaltoLunaNodeRegistrationTest : AbstractNodeRegistrationTest() {

    override fun configPath(): Path = javaClass.getResource("gemalto.conf").toPath()

    override fun getCryptoService(x500Principal: X500Principal, config: Path): CryptoService {
        return GemaltoLunaCryptoService.fromConfigurationFile(aliceName.x500Principal, configPath())
    }

    override fun cryptoServiceName(): String = SupportedCryptoServices.GEMALTO_LUNA.name

    override fun deleteExistingEntries() {
        val config = ConfigFactory.parseFile(configPath().toFile()).resolve().parseAs(GemaltoConfig::class)
        val provider = LunaProvider.getInstance()
        val keyStore = KeyStore.getInstance(GemaltoLunaCryptoService.KEYSTORE_TYPE, provider)
        keyStore.load(config.keyStore.byteInputStream(), config.password.toCharArray())
        val identityKeyAlias = "identity-private-key"
        if (keyStore.containsAlias(identityKeyAlias))
            keyStore.deleteEntry(identityKeyAlias)
    }

    /**
     * The below is only needed when JCA provider for Gemalto is not installed system-wide.
     *
     */
//    override val systemProperties: Map<String, String>
//        get() = mapOf(
//                "java.library.path" to "/<path_to_gemalto_installation>/gemalto/lunaclient/jsp/lib"
//        )

    data class GemaltoConfig(val keyStore: String, val password: String)
}
