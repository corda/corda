package net.corda.node.services.keys.cryptoservice.utimaco

import com.typesafe.config.ConfigFactory
import net.corda.core.internal.toPath
import net.corda.node.services.keys.cryptoservice.AbstractNodeRegistrationTest
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.SupportedCryptoServices
import net.corda.nodeapi.internal.cryptoservice.utimaco.UtimacoCryptoService
import net.corda.nodeapi.internal.hsm.HsmSimulator
import org.apache.commons.io.FileUtils
import org.junit.Ignore
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.charset.Charset
import java.nio.file.Path
import javax.security.auth.x500.X500Principal

@Ignore
class UtimacoNodeRegistrationTest : AbstractNodeRegistrationTest() {

    private var tmpConfig: Path? = null

    @Rule
    @JvmField
    val configFolder = TemporaryFolder()

    @Rule
    @JvmField
    val hsmSimulator = HsmSimulator(portAllocation)

    override fun configPath(): Path = createTempUtimacoConfig()

    override fun getCryptoService(x500Principal: X500Principal, config: Path): CryptoService {
        return UtimacoCryptoService.fromConfigurationFile(configPath())
    }

    override fun cryptoServiceName(): String = SupportedCryptoServices.UTIMACO.name

    override fun deleteExistingEntries() {
        // no need to cleanup, since an ephemeral simulator is used
    }

    private fun createTempUtimacoConfig(): Path {
        if (tmpConfig != null) {
            return tmpConfig!!
        }

        val utimacoConfig = ConfigFactory.parseFile(javaClass.getResource("utimaco.conf").toPath().toFile())
        val portConfig = ConfigFactory.parseMap(mapOf("port" to hsmSimulator.address.port))
        val config = portConfig.withFallback(utimacoConfig)
        val tmpConfigFile = configFolder.newFile("utimaco_updated.conf")
        FileUtils.writeStringToFile(tmpConfigFile, config.root().render(), Charset.defaultCharset())
        tmpConfig = tmpConfigFile.toPath()
        return tmpConfig!!
    }

}
