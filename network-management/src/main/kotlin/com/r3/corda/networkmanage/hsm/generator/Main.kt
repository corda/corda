package com.r3.corda.networkmanage.hsm.generator

import com.r3.corda.networkmanage.hsm.authentication.CryptoServerProviderConfig
import com.r3.corda.networkmanage.hsm.utils.mapCryptoServerException
import org.apache.logging.log4j.LogManager
import java.nio.file.Paths

private val log = LogManager.getLogger("com.r3.corda.networkmanage.hsm.generator.Main")

fun main(args: Array<String>) {
    val commandLineOptions = parseCommandLine(*args)
    parseParameters(commandLineOptions.configFile).run {
        val providerConfig = CryptoServerProviderConfig(
                Device = "$hsmPort@$hsmHost",
                KeySpecifier = certConfig.keySpecifier,
                KeyGroup = certConfig.keyGroup,
                StoreKeysExternal = certConfig.storeKeysExternal)
        try {
            val authenticator = AutoAuthenticator(providerConfig, userConfigs)
            authenticator.connectAndAuthenticate { provider ->
                val generator = KeyCertificateGenerator(this)
                generator.generate(provider)
            }
        } catch (e: Exception) {
            log.error(mapCryptoServerException(e))
        }
    }
}