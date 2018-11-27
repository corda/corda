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
        hashToParametersMap[currentHash] = currentParameters
    }

    override val currentHash: SecureHash get() = currentParameters.serialize().hash
    override val defaultHash: SecureHash get() = currentHash
    override fun getEpochFromHash(hash: SecureHash): Int? = lookup(hash)?.epoch
    override fun lookup(hash: SecureHash): NetworkParameters? = hashToParametersMap[hash]
    override fun saveParameters(signedNetworkParameters: SignedDataWithCert<NetworkParameters>) {
        val networkParameters = signedNetworkParameters.verified()
        val hash = signedNetworkParameters.raw.hash
        hashToParametersMap[hash] = networkParameters
    }
}