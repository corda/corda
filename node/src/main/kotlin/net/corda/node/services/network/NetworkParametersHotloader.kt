package net.corda.node.services.network

import net.corda.core.crypto.SecureHash
import net.corda.core.internal.NetworkParametersStorage
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.core.utilities.contextLogger
import net.corda.node.internal.NetworkParametersReader
import net.corda.nodeapi.internal.network.verifiedNetworkParametersCert
import java.security.cert.X509Certificate
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaGetter

/**
 * This class is responsible for hotloading new network parameters or shut down the node if it's not possible.
 * Currently only hotloading notary changes are supported.
 */
class NetworkParametersHotloader(private val networkMapClient: NetworkMapClient,
                                 private val trustRoots: List<X509Certificate>,
                                 @Volatile private var networkParameters: NetworkParameters,
                                 private val networkParametersReader: NetworkParametersReader,
                                 private val networkParametersStorage: NetworkParametersStorage) {
    companion object {
        private val logger = contextLogger()
        private val alwaysHotloadable = listOf(NetworkParameters::epoch, NetworkParameters::modifiedTime)
    }

    private val networkParameterUpdateListeners = mutableListOf<NetworkParameterUpdateListener>()
    private val notaryUpdateListeners = mutableListOf<NotaryUpdateListener>()

    fun addNetworkParametersChangedListeners(listener: NetworkParameterUpdateListener) {
        networkParameterUpdateListeners.add(listener)
    }

    fun addNotaryUpdateListener(listener: NotaryUpdateListener) {
        notaryUpdateListeners.add(listener)
    }

    private fun notifyListenersFor(notaries: List<NotaryInfo>) = notaryUpdateListeners.forEach { it.onNewNotaryList(notaries) }
    private fun notifyListenersFor(networkParameters: NetworkParameters) = networkParameterUpdateListeners.forEach { it.onNewNetworkParameters(networkParameters) }

    fun attemptHotload(newNetworkParameterHash: SecureHash): Boolean {

        val newSignedNetParams = networkMapClient.getNetworkParameters(newNetworkParameterHash)
        val newNetParams = newSignedNetParams.verifiedNetworkParametersCert(trustRoots)

        if (canHotload(newNetParams)) {
            logger.info("All changed parameters are hotloadable")
            hotloadParameters(newNetParams)
            return true
        } else {
            return false
        }
    }

    /**
     * Ignoring always hotloadable properties (epoch, modifiedTime) return true if the notary is the only property that is different in the new network parameters
     */
    private fun canHotload(newNetworkParameters: NetworkParameters): Boolean {

        val propertiesChanged = NetworkParameters::class.declaredMemberProperties
                .minus(alwaysHotloadable)
                .filter { networkParameters.valueChanged(newNetworkParameters, it.javaGetter) }

        logger.info("Updated NetworkParameters properties: $propertiesChanged")

        val noPropertiesChanged = propertiesChanged.isEmpty()
        val onlyNotariesChanged = propertiesChanged == listOf(NetworkParameters::notaries)
        return when {
            noPropertiesChanged -> true
            onlyNotariesChanged -> true
            else -> false
        }
    }

    /**
     * Update local networkParameters and currentParametersHash with new values.
     * Notify all listeners for network parameter changes
     */
    private fun hotloadParameters(newNetworkParameters: NetworkParameters) {

        networkParameters = newNetworkParameters
        val networkParametersAndSigned = networkParametersReader.read()
        networkParametersStorage.setCurrentParameters(networkParametersAndSigned.signed, trustRoots)
        notifyListenersFor(newNetworkParameters)
        notifyListenersFor(newNetworkParameters.notaries)
    }
}