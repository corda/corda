package net.corda.node.services.network

import net.corda.cliutils.ExitCodes
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.NetworkParametersStorage
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.internal.readObject
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.core.utilities.contextLogger
import net.corda.node.internal.NetworkParametersReader
import net.corda.nodeapi.internal.network.NETWORK_PARAMS_FILE_NAME
import net.corda.nodeapi.internal.network.NETWORK_PARAMS_UPDATE_FILE_NAME

import net.corda.nodeapi.internal.network.SignedNetworkParameters
import net.corda.nodeapi.internal.network.verifiedNetworkParametersCert
import java.nio.file.Path
import java.security.cert.X509Certificate

import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaGetter
import kotlin.system.exitProcess

/**
 * This class is responsible for hotloading new network parameters or shut down the node if it's not possible.
 * Currently only hotloading notary changes are supported.
 */
class NetworkParametersHotloader(baseDirectory: Path,
                                 private val networkMapClient: NetworkMapClient?,
                                 private val trustRoot: X509Certificate,
                                 private var networkParameters: NetworkParameters,
                                 private var parametersHash: SecureHash,
                                 private val networkParametersReader: NetworkParametersReader,
                                 private val networkParametersStorage: NetworkParametersStorage) {
    companion object {
        private val logger = contextLogger()
        private val alwaysHotloadable = listOf(NetworkParameters::epoch, NetworkParameters::modifiedTime)
    }

    private val networkParameterUpdateListeners = mutableListOf<NetworkParameterUpdateListener>()
    private val notaryUpdateListeners = mutableListOf<NotaryListUpdateListener>()
    private val updatesFile = baseDirectory / NETWORK_PARAMS_UPDATE_FILE_NAME

    fun addNetworkParametersChangedListeners(listener: NetworkParameterUpdateListener) {
        networkParameterUpdateListeners.add(listener)
    }

    fun addNotaryUpdateListener(listener: NotaryListUpdateListener) {
        notaryUpdateListeners.add(listener)
    }

    private fun notifyListenersFor(notaries: List<NotaryInfo>) = notaryUpdateListeners.forEach { it.onNewNotaryList(notaries) }
    private fun notifyListenersFor(networkParameters: NetworkParameters) = networkParameterUpdateListeners.forEach { it.onNewNetworkParameters(networkParameters) }

    /**
     * When the network parameter hash has changed, try to hotload the new network parameters, or shut down if it's not possible.
     */
    fun update(newNetworkParameterHash: SecureHash) {
        logger.info("update newNetworkParameterHash: $newNetworkParameterHash")
        if (parametersHash != newNetworkParameterHash) {
            hotloadParametersOrExit(newNetworkParameterHash)
        }
    }

    /**
     * Ignoring always hotloadable properties (epoch, modifiedTime) return true if the notary is the only property that is different in the new network parameters
     */
    @VisibleForTesting
    fun canHotload(newNetworkParameters: NetworkParameters): Boolean {

        if (notaryUpdateListeners.isEmpty()) {
            logger.warn("There is no update function assigned to notary changes.")
        }
        val propertiesChanged = NetworkParameters::class.declaredMemberProperties
                .minus(alwaysHotloadable)
                .filter { networkParameters.valueChanged(newNetworkParameters, it.javaGetter) }

        logger.info("Updated NetworkParameters properties: $propertiesChanged")

        val noPropertiesChanged = propertiesChanged.isEmpty()
        val onlyNotariesChanged = propertiesChanged == listOf(NetworkParameters::notaries)
        return when {
            noPropertiesChanged -> true
            onlyNotariesChanged && notaryUpdateListeners.isNotEmpty() -> true
            else -> false
        }
    }

    private fun hotloadParametersOrExit(newNetworkParameterHash: SecureHash) {


        val nodeAcceptedNewParameters = updatesFile.exists() && newNetworkParameterHash == updatesFile.readObject<SignedNetworkParameters>().raw.hash

        if (nodeAcceptedNewParameters) {

            networkMapClient
                    ?: throw IllegalStateException("Network parameters hotloading are not supported without compatibility zone configured")

            logger.info("Flag day occurred. Network map switched to the new network parameters: " +
                    "${newNetworkParameterHash}.")


            val newSignedNetParams = networkMapClient.getNetworkParameters(newNetworkParameterHash)

            val newNetParams = newSignedNetParams.verifiedNetworkParametersCert(trustRoot)
            if (canHotload(newNetParams)) {
                logger.info("All changed parameters are hotloadable")
                hotloadParameters(newNetParams, newNetworkParameterHash)
            } else {
                logger.info("Not all changed network parameters can be hotloaded. Node will shutdown now and needs to be started again.")
                exitProcess(ExitCodes.SUCCESS)
            }
        } else {
            logger.error(
                    """Node is using network parameters with hash $parametersHash but the network map is advertising ${newNetworkParameterHash}.
To resolve this mismatch, and move to the current parameters, delete the $NETWORK_PARAMS_FILE_NAME file from the node's directory and restart.
The node will shutdown now.""")
            exitProcess(ExitCodes.FAILURE)
        }
    }

    /**
     * Update local networkParameters and currentParametersHash with new values.
     * Notify all listeners for network parameter changes
     */
    private fun hotloadParameters(newNetworkParameters: NetworkParameters, newNetworkParameterHash: SecureHash) {

        networkParameters = newNetworkParameters
        parametersHash = newNetworkParameterHash

        val networkParametersAndSigned = networkParametersReader.read()
        networkParametersStorage.setCurrentParameters(networkParametersAndSigned.signed, trustRoot)

        notifyListenersFor(newNetworkParameters)
        notifyListenersFor(newNetworkParameters.notaries)
    }
}