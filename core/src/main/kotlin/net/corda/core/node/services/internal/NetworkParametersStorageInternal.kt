package net.corda.core.node.services.internal

import net.corda.core.crypto.SecureHash
import net.corda.core.internal.SignedDataWithCert
import net.corda.core.node.NetworkParameters
import net.corda.core.node.services.NetworkParametersStorage
import java.security.cert.X509Certificate

interface NetworkParametersStorageInternal : NetworkParametersStorage {
    /**
     * Return parameters epoch for the given parameters hash. Null if there are no parameters for this hash in the storage and we are unable to
     * get them from network map.
     */
    fun getEpochFromHash(hash: SecureHash): Int?

    /**
     * Save signed network parameters data. Internally network parameters bytes should be stored with the signature.
     * It's because of ability of older nodes to function in network where parameters were extended with new fields.
     * Hash should always be calculated over the serialized bytes.
     */
    fun saveParameters(signedNetworkParameters: SignedDataWithCert<NetworkParameters>)

    fun setCurrentParameters(currentSignedParameters: SignedDataWithCert<NetworkParameters>, trustRoot: X509Certificate)

    /**
     * Return signed network parameters with certificate for the given hash. Null if there are no parameters for this hash in the storage.
     * (No fallback to network map.)
     */
    fun lookupSigned(hash: SecureHash): SignedDataWithCert<NetworkParameters>?

    /**
     * Checks if parameters with given hash are in the storage.
     */
    fun hasParameters(hash: SecureHash): Boolean
}