package com.r3.corda.networkmanage.common.signer

import com.r3.corda.networkmanage.common.persistence.CertificateStatus
import com.r3.corda.networkmanage.common.persistence.NetworkMapStorage
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.serialize

/**
 * Encapsulates the network map signing procedure.
 * To sign a network map following steps need to be done:
 * 1) Collect all node info data that has been signed and has valid certificates
 * 2) Retrieve most up-to-date network parameters
 * 3) Sign hashed version of the network map
 * 4) Persist network map data together with its signature
 * Once the network map is signed it is considered to be the current network map.
 *
 * This class resides in the common package as it is intended to be used in both local and distributed deployments.
 * This means that it can be executed by a remote (e.g. HSM) signing service or locally by Doorman.
 */
@CordaSerializable
data class NetworkMap(val nodeInfoHashes: List<String>, val parametersHash: String)

@CordaSerializable
data class SignedNetworkMap(val networkMap: NetworkMap, val signatureData: SignatureAndCertPath)

class NetworkMapSigner(private val networkMapStorage: NetworkMapStorage,
                       private val signer: Signer) {
    /**
     * Signs the network map.
     */
    fun signNetworkMap() {
        val currentSignedNetworkMap = networkMapStorage.getCurrentNetworkMap()
        val currentNetworkMapValidNodeInfo = networkMapStorage.getCurrentNetworkMapNodeInfoHashes(listOf(CertificateStatus.VALID))
        val detachedValidNodeInfo = networkMapStorage.getDetachedSignedAndValidNodeInfoHashes()
        val nodeInfoHashes = currentNetworkMapValidNodeInfo + detachedValidNodeInfo
        val networkParameters = networkMapStorage.getLatestNetworkParameters()
        val networkMap = NetworkMap(nodeInfoHashes.map { it.toString() }, networkParameters.serialize().hash.toString())
        if (currentSignedNetworkMap == null || networkMap != currentSignedNetworkMap.networkMap) {
            val digitalSignature = signer.sign(networkMap.serialize().bytes)
            require(digitalSignature != null) { "Error while signing network map." }
            val signedHashedNetworkMap = SignedNetworkMap(networkMap, digitalSignature!!)
            networkMapStorage.saveNetworkMap(signedHashedNetworkMap)
        }
    }
}