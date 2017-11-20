package net.corda.nodeapi.internal

import net.corda.core.crypto.SignedData
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.crypto.sign
import net.corda.core.internal.copyTo
import net.corda.core.internal.div
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.serialize
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

    fun install(dir: Path) {
        try {
            serializedNetworkParameters.open().copyTo(dir / "network-parameters")
        } catch (e: FileAlreadyExistsException) {
            // Leave the file untouched if it already exists
        }
    }
}