package net.corda.ext.api.network

import net.corda.core.CordaInternal
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.ParametersUpdateInfo
import java.time.Duration

/**
 * Selected operations to interact with NetworkMap
 */
@CordaInternal
interface NetworkMapOperations {
    /**
     * Obtains snapshot and subscribes for `NetworkParameters` updates
     */
    fun trackParametersUpdate(): DataFeed<ParametersUpdateInfo?, ParametersUpdateInfo>

    /**
     * Having received new network parameters, acknowledges acceptance of them.
     *
     * @param parametersHash hash identifying `NetworkParameters` being accepted
     * @param sign callback which allows ro sign accepted cache using identity key of the node
     */
    fun acceptNewNetworkParameters(parametersHash: SecureHash, sign: (SecureHash) -> SignedData<SecureHash>)

    /**
     * Forcibly performs update of the network map cache
     *
     * @return duration till which cache deemed to be valid
     */
    fun updateNetworkMapCache(): Duration
}