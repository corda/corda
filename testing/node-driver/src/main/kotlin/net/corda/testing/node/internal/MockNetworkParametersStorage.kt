package net.corda.testing.node.internal

import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.internal.SignedDataWithCert
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.core.serialization.serialize
import net.corda.node.internal.NetworkParametersStorageInternal
import net.corda.nodeapi.internal.network.verifiedNetworkMapCert
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.withTestSerializationEnvIfNotSet
import java.security.cert.X509Certificate
import java.time.Instant

class MockNetworkParametersStorage(private var currentParameters: NetworkParameters = testNetworkParameters(modifiedTime = Instant.MIN)) : NetworkParametersStorageInternal {
    private val hashToParametersMap: HashMap<SecureHash, NetworkParameters> = HashMap()

    init {
        storeCurrentParameters()
    }

    fun setCurrentParametersUnverified(networkParameters: NetworkParameters) {
        currentParameters = networkParameters
        storeCurrentParameters()
    }

    override fun setCurrentParameters(currentSignedParameters: SignedDataWithCert<NetworkParameters>, trustRoot: X509Certificate) {
        setCurrentParametersUnverified(currentSignedParameters.verifiedNetworkMapCert(trustRoot))
    }

    override val currentHash: SecureHash
        get() {
            return withTestSerializationEnvIfNotSet("networkParameters") {
                currentParameters.serialize().hash
            }
        }
    override val defaultHash: SecureHash get() = currentHash
    override fun getEpochFromHash(hash: SecureHash): Int? = lookup(hash)?.epoch
    override fun lookup(hash: SecureHash): NetworkParameters? = hashToParametersMap[hash]
    override fun saveParameters(signedNetworkParameters: SignedDataWithCert<NetworkParameters>) {
        val networkParameters = signedNetworkParameters.verified()
        val hash = signedNetworkParameters.raw.hash
        hashToParametersMap[hash] = networkParameters
    }

    override fun getHistoricNotary(party: Party): NotaryInfo? {
        val inCurrentParams = currentParameters.notaries.singleOrNull { it.identity == party }
        if (inCurrentParams == null) {
            val inOldParams = hashToParametersMap.flatMap { (_, parameters) ->
                parameters.notaries
            }.firstOrNull { it.identity == party }
            return inOldParams
        } else return inCurrentParams
    }

    private fun storeCurrentParameters() {
        hashToParametersMap[currentHash] = currentParameters
    }
}