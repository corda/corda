package net.corda.testing.node.internal

import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.internal.NetworkParametersStorage
import net.corda.core.internal.SignedDataWithCert
import net.corda.core.internal.signWithCert
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import net.corda.nodeapi.internal.network.verifiedNetworkMapCert
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.withTestSerializationEnvIfNotSet
import java.security.cert.X509Certificate
import java.time.Instant

class MockNetworkParametersStorage(private var currentParameters: NetworkParameters = testNetworkParameters(modifiedTime = Instant.MIN)) : NetworkParametersStorage {
    private val hashToParametersMap: HashMap<SecureHash, NetworkParameters> = HashMap()
    private val hashToSignedParametersMap: HashMap<SecureHash, SignedNetworkParameters> = HashMap()
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

    override fun lookupSigned(hash: SecureHash): SignedDataWithCert<NetworkParameters>? {
        return hashToSignedParametersMap[hash]
    }

    override fun hasParameters(hash: SecureHash): Boolean = hash in hashToParametersMap

    override val currentHash: SecureHash
        get() {
            return withTestSerializationEnvIfNotSet {
                currentParameters.serialize().hash
            }
        }
    override val defaultHash: SecureHash get() = currentHash
    override fun lookup(hash: SecureHash): NetworkParameters? = hashToParametersMap[hash]
    override fun getEpochFromHash(hash: SecureHash): Int? = lookup(hash)?.epoch
    override fun saveParameters(signedNetworkParameters: SignedDataWithCert<NetworkParameters>) {
        val networkParameters = signedNetworkParameters.verified()
        val hash = signedNetworkParameters.raw.hash
        hashToParametersMap[hash] = networkParameters
        hashToSignedParametersMap[hash] = signedNetworkParameters
    }

    override fun getHistoricNotary(party: Party): NotaryInfo? {
        val inCurrentParams = currentParameters.notaries.singleOrNull { it.identity == party }
        return if (inCurrentParams == null) {
            val inOldParams = hashToParametersMap.flatMap { (_, parameters) ->
                parameters.notaries
            }.firstOrNull { it.identity == party }
            inOldParams
        } else {
            inCurrentParams
        }
    }

    private fun storeCurrentParameters() {
        hashToParametersMap[currentHash] = currentParameters
        val testIdentity = TestIdentity(ALICE_NAME, 20)
        val signedData = withTestSerializationEnvIfNotSet {
            currentParameters.signWithCert(testIdentity.keyPair.private, testIdentity.identity.certificate)
        }
        hashToSignedParametersMap[currentHash] = signedData
    }
}
