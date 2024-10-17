package net.corda.node.internal

import net.corda.core.crypto.SecureHash
import net.corda.core.internal.copyTo
import net.corda.core.internal.readObject
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import net.corda.node.services.network.NetworkMapClient
import net.corda.nodeapi.internal.network.NETWORK_PARAMS_FILE_NAME
import net.corda.nodeapi.internal.network.NETWORK_PARAMS_UPDATE_FILE_NAME
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import net.corda.nodeapi.internal.network.verifiedNetworkParametersCert
import java.nio.file.Path
import java.security.cert.X509Certificate
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.moveTo

class NetworkParametersReader(private val trustRoots: Set<X509Certificate>,
                              private val networkMapClient: NetworkMapClient?,
                              private val networkParamsPath: Path) {
    companion object {
        private val logger = contextLogger()
    }

    sealed class Error(message: String) : Exception(message) {
        class ParamsNotConfigured : Error("Couldn't find network parameters file and compatibility zone wasn't configured/isn't reachable.")
        class NetworkMapNotConfigured : Error("Node hasn't been configured to connect to a network map from which to get the network parameters.")
        class OldParamsAndUpdate : Error(
                "Both network parameters and network parameters update files don't match" +
                        "parameters advertised by network map. Please update node to use correct network parameters file."
        )
        class OldParams(previousParametersHash: SecureHash, advertisedParametersHash: SecureHash, path: Path) : Error(
                """Node is using network parameters with hash $previousParametersHash but the network map is advertising $advertisedParametersHash.
                To resolve this mismatch, and move to the current parameters, delete the network-parameters file at location $path and restart."""
        )
    }

    private val networkParamsFile = networkParamsPath / NETWORK_PARAMS_FILE_NAME

    fun read(): NetworkParametersAndSigned {
        val advertisedParametersHash = try {
            networkMapClient?.getNetworkMap()?.payload?.networkParameterHash
        } catch (e: Exception) {
            logger.warn("Unable to download network map. Node will attempt to start using network-parameters file: $e")
            // If NetworkMap is down while restarting the node, we should be still able to continue with parameters from file
            null
        }
        val signedParametersFromFile = if (networkParamsFile.exists()) {
            networkParamsFile.readObject<SignedNetworkParameters>()
        } else {
            null
        }
        val signedParameters = if (advertisedParametersHash != null) {
            // TODO On one hand we have node starting without parameters and just accepting them by default,
            //  on the other we have parameters update process - it needs to be unified. Say you start the node, you don't have matching parameters,
            //  you get them from network map, but you have to run the approval step.
            if (signedParametersFromFile == null) { // Node joins for the first time.
                downloadParameters(advertisedParametersHash)
            } else if (signedParametersFromFile.raw.hash == advertisedParametersHash) { // Restarted with the same parameters.
                signedParametersFromFile
            } else { // Update case.
                readParametersUpdate(advertisedParametersHash, signedParametersFromFile.raw.hash)
            }
        } else { // No compatibility zone configured. Node should proceed with parameters from file.
            signedParametersFromFile ?: throw Error.ParamsNotConfigured()
        }

        return NetworkParametersAndSigned(signedParameters, trustRoots)
    }

    private fun readParametersUpdate(advertisedParametersHash: SecureHash, previousParametersHash: SecureHash): SignedNetworkParameters {
        val parametersUpdateFile = networkParamsPath / NETWORK_PARAMS_UPDATE_FILE_NAME
        if (!parametersUpdateFile.exists()) {
            throw Error.OldParams(previousParametersHash, advertisedParametersHash, networkParamsPath)
        }
        val signedUpdatedParameters = parametersUpdateFile.readObject<SignedNetworkParameters>()
        if (signedUpdatedParameters.raw.hash != advertisedParametersHash) {
            throw Error.OldParamsAndUpdate()
        }
        parametersUpdateFile.moveTo(networkParamsFile, overwrite = true)
        logger.info("Scheduled update to network parameters has occurred - node now updated to these new parameters.")
        return signedUpdatedParameters
    }

    // Used only when node joins for the first time.
    private fun downloadParameters(parametersHash: SecureHash): SignedNetworkParameters {
        logger.info("No network-parameters file found. Expecting network parameters to be available from the network map.")
        networkMapClient ?: throw Error.NetworkMapNotConfigured()
        val signedParams = networkMapClient.getNetworkParameters(parametersHash)
        signedParams.verifiedNetworkParametersCert(trustRoots)
        networkParamsFile.parent.toFile().mkdirs()
        signedParams.serialize().open().copyTo(networkParamsFile)
        logger.info("Saved network parameters into: $networkParamsFile")
        return signedParams
    }

    // By passing in just the SignedNetworkParameters object, this class guarantees that the networkParameters property
    // could have only been derived from it.
    class NetworkParametersAndSigned(val signed: SignedNetworkParameters, trustRoots: Set<X509Certificate>) {
        // for backwards compatibility we allow netparams to be signed with the networkmap cert,
        // but going forwards we also accept the distinct netparams cert as well
        val networkParameters: NetworkParameters = signed.verifiedNetworkParametersCert(trustRoots)
        operator fun component1() = networkParameters
        operator fun component2() = signed
    }
}
