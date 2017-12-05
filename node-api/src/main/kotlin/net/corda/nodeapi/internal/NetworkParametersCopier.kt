package net.corda.nodeapi.internal

import net.corda.core.crypto.SignedData
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.crypto.sign
import net.corda.core.internal.copyTo
import net.corda.core.internal.div
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.NetworkParameters
import java.math.BigInteger
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path

class NetworkParametersCopier(networkParameters: NetworkParameters) {
    private companion object {
        val DUMMY_MAP_KEY = entropyToKeyPair(BigInteger.valueOf(123))
    }

    private val serializedNetworkParameters = networkParameters.let {
        val serialize = it.serialize()
        val signature = DUMMY_MAP_KEY.sign(serialize)
        SignedData(serialize, signature).serialize()
    }

    fun install(dir: Path, paramsFile: String = NETWORK_PARAM_FILE_PREFIX) {
        try {
            serializedNetworkParameters.open().copyTo(dir / paramsFile)
        } catch (e: FileAlreadyExistsException) {
            // Leave the file untouched if it already exists
        }
    }
}