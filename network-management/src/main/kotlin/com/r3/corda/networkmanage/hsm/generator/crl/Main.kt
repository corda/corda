/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.hsm.generator.crl

import com.r3.corda.networkmanage.common.configuration.ConfigFilePathArgsParser
import com.r3.corda.networkmanage.common.utils.Revocation
import com.r3.corda.networkmanage.common.utils.createSignedCrl
import com.r3.corda.networkmanage.hsm.authentication.CryptoServerProviderConfig
import com.r3.corda.networkmanage.hsm.generator.AutoAuthenticator
import com.r3.corda.networkmanage.hsm.signer.HsmSigner
import com.r3.corda.networkmanage.hsm.utils.mapCryptoServerException
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.apache.commons.io.FileUtils
import org.apache.logging.log4j.LogManager
import java.math.BigInteger
import java.security.cert.CRLReason
import java.time.Duration
import java.util.*

private val logger = LogManager.getLogger("com.r3.corda.networkmanage.hsm.generator.crl.Main")

fun main(args: Array<String>) {
    run(parseParameters(ConfigFilePathArgsParser().parseOrExit(*args)))
}

fun run(parameters: GeneratorConfig) {
    parameters.run {
        val providerConfig = CryptoServerProviderConfig(
                Device = "$hsmPort@$hsmHost",
                KeySpecifier = crl.keySpecifier,
                KeyGroup = crl.keyGroup)
        try {
            AutoAuthenticator(providerConfig, userConfigs).connectAndAuthenticate { provider ->
                logger.info("Generating an empty CRL...")
                val issuerCertificate = loadTrustStore().getCertificate(X509Utilities.CORDA_ROOT_CA)
                val generatedCrl = createSignedCrl(issuerCertificate,
                        crl.crlEndpoint,
                        Duration.ofDays(crl.validDays),
                        HsmSigner(provider = provider, keyName = X509Utilities.CORDA_ROOT_CA),
                        crl.revocations.map { Revocation(
                                BigInteger(it.certificateSerialNumber),
                                Date(it.dateInMillis),
                                CRLReason.valueOf(it.reason)
                        ) },
                        crl.indirectIssuer)
                FileUtils.writeByteArrayToFile(crl.filePath.toFile(), generatedCrl.encoded)
                provider.logoff()
            }
        } catch (e: Exception) {
            logger.error("HSM CRL generation error.", mapCryptoServerException(e))
        }
    }
}