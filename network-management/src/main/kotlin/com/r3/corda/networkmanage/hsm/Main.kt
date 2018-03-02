package com.r3.corda.networkmanage.hsm

import com.google.common.primitives.Booleans
import com.r3.corda.networkmanage.common.persistence.configureDatabase
import com.r3.corda.networkmanage.common.utils.ShowHelpException
import com.r3.corda.networkmanage.common.utils.initialiseSerialization
import com.r3.corda.networkmanage.hsm.configuration.parseParameters
import com.r3.corda.networkmanage.hsm.processor.CsrProcessor
import com.r3.corda.networkmanage.hsm.processor.NetworkMapProcessor
import org.apache.logging.log4j.LogManager
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import javax.crypto.Cipher

private val logger = LogManager.getLogger("com.r3.corda.networkmanage.hsm.Main")

fun main(args: Array<String>) {
    parseParameters(*args).run {
        try {
            // Validate
            // Grabbed from https://stackoverflow.com/questions/7953567/checking-if-unlimited-cryptography-is-available
            require(Cipher.getMaxAllowedKeyLength("AES") >= 256) {
                "Unlimited Strength Jurisdiction Policy Files must be installed, see http://www.oracle.com/technetwork/java/javase/downloads/jce8-download-2133166.html"
            }
            require(Booleans.countTrue(doorman != null, networkMap != null) == 1) {
                "Exactly one networkMap or doorman configuration needs to be specified."
            }
            requireNotNull(dataSourceProperties)

            // Ensure the BouncyCastle provider is installed
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }

            initialiseSerialization()
            // Create DB connection.
            val persistence = configureDatabase(dataSourceProperties, database)
            if (networkMap != null) {
                NetworkMapProcessor(networkMap, device, keySpecifier, persistence).run()
            } else {
                try {
                    CsrProcessor(doorman!!, device, keySpecifier, persistence).showMenu()
                } catch (e: ShowHelpException) {
                    e.errorMessage?.let(::println)
                    e.parser.printHelpOn(System.out)
                }
            }
        } catch (e: Exception) {
            logger.error("Error while starting the HSM Signing service.", e)
        }
    }
}


