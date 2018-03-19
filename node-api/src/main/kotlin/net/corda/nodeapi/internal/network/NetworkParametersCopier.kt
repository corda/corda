/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.network

import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.copyTo
import net.corda.core.internal.div
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.createDevNetworkMapCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class NetworkParametersCopier(
        networkParameters: NetworkParameters,
        signingCertAndKeyPair: CertificateAndKeyPair = createDevNetworkMapCa(),
        overwriteFile: Boolean = false,
        @VisibleForTesting
        val update: Boolean = false
) {
    private val copyOptions = if (overwriteFile) arrayOf(StandardCopyOption.REPLACE_EXISTING) else emptyArray()
    private val serialisedSignedNetParams = signingCertAndKeyPair.sign(networkParameters).serialize()

    fun install(nodeDir: Path) {
        val fileName = if (update) NETWORK_PARAMS_UPDATE_FILE_NAME else NETWORK_PARAMS_FILE_NAME
        try {
            serialisedSignedNetParams.open().copyTo(nodeDir / fileName, *copyOptions)
        } catch (e: FileAlreadyExistsException) {
            // This is only thrown if the file already exists and we didn't specify to overwrite it. In that case we
            // ignore this exception as we're happy with the existing file.
        }
    }
}
