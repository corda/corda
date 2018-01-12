package net.corda.nodeapi.internal.network

import net.corda.core.internal.copyTo
import net.corda.core.internal.div
import net.corda.core.internal.signWithCert
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.createDevNetworkMapCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class NetworkParametersCopier(
        networkParameters: NetworkParameters,
        networkMapCa: CertificateAndKeyPair = createDevNetworkMapCa(),
        overwriteFile: Boolean = false
) {
    private val copyOptions = if (overwriteFile) arrayOf(StandardCopyOption.REPLACE_EXISTING) else emptyArray()
    private val serialisedSignedNetParams = networkParameters.signWithCert(
            networkMapCa.keyPair.private,
            networkMapCa.certificate
    ).serialize()

    fun install(nodeDir: Path) {
        try {
            serialisedSignedNetParams.open().copyTo(nodeDir / NETWORK_PARAMS_FILE_NAME, *copyOptions)
        } catch (e: FileAlreadyExistsException) {
            // This is only thrown if the file already exists and we didn't specify to overwrite it. In that case we
            // ignore this exception as we're happy with the existing file.
        }
    }
}
