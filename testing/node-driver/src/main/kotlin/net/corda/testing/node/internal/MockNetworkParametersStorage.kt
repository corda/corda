package net.corda.testing.node.internal

import net.corda.core.crypto.SecureHash
import net.corda.core.internal.SignedDataWithCert
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.serialize
import net.corda.node.internal.NetworkParametersStorageInternal
import net.corda.testing.common.internal.testNetworkParameters
import java.time.Instant

class MockNetworkParametersStorage(val currentParameters: NetworkParameters = testNetworkParameters(modifiedTime = Instant.MIN)) : NetworkParametersStorageInternal {
    private val hashToParametersMap: HashMap<SecureHash, NetworkParameters> = HashMap()

    init {
        hashToParametersMap[currentParametersHash] = currentParameters
    }

    override val currentParametersHash: SecureHash get() = currentParameters.serialize().hash
    override val defaultParametersHash: SecureHash get() = currentParametersHash
    override fun getEpochFromHash(hash: SecureHash): Int? = readParametersFromHash(hash)?.epoch
    override fun readParametersFromHash(hash: SecureHash): NetworkParameters? = hashToParametersMap[hash]
    override fun saveParameters(signedNetworkParameters: SignedDataWithCert<NetworkParameters>) {
        val networkParameters = signedNetworkParameters.verified()
        val hash = signedNetworkParameters.raw.hash
        hashToParametersMap[hash] = networkParameters
    }
}