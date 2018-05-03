package com.r3.corda.networkmanage.doorman

import com.r3.corda.networkmanage.common.persistence.CertificateSigningRequestStorage
import com.r3.corda.networkmanage.common.persistence.NetworkMapStorage
import com.r3.corda.networkmanage.common.persistence.entity.UpdateStatus
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.core.internal.CertRole
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.crypto.x509Certificates
import java.nio.file.Path
import java.time.Instant

class ParametersUpdateHandler(val csrStorage: CertificateSigningRequestStorage, val networkMapStorage: NetworkMapStorage) {
    companion object {
        private val logger = contextLogger()
    }

    fun loadParametersFromFile(file: Path): NetworkParametersCmd.Set {
        val netParamsConfig = ConfigFactory.parseFile(file.toFile(), ConfigParseOptions.defaults())
                .parseAs<NetworkParametersConfig>()
        checkNotaryCertificates(netParamsConfig.notaries.map { it.nodeInfo })
        return NetworkParametersCmd.Set.fromConfig(netParamsConfig)
    }

    fun processNetworkParameters(networkParametersCmd: NetworkParametersCmd) {
        when (networkParametersCmd) {
            is NetworkParametersCmd.Set -> handleSetNetworkParameters(networkParametersCmd)
            NetworkParametersCmd.FlagDay -> handleFlagDay()
            NetworkParametersCmd.CancelUpdate -> handleCancelUpdate()
        }
    }

    private fun checkNotaryCertificates(notaryNodeInfos: List<NodeInfo>) {
        notaryNodeInfos.forEach { notaryInfo ->
            val cert = notaryInfo.legalIdentitiesAndCerts.last().certPath.x509Certificates.find {
                val certRole = CertRole.extract(it)
                certRole == CertRole.SERVICE_IDENTITY || certRole == CertRole.NODE_CA
            }
            cert ?: throw IllegalArgumentException("The notary certificate path does not contain SERVICE_IDENTITY or NODE_CA role in it")
            csrStorage.getValidCertificatePath(cert.publicKey)
                    ?: throw IllegalArgumentException("Notary with node info: $notaryInfo is not registered with the doorman")
        }
    }

    private fun handleSetNetworkParameters(setNetParams: NetworkParametersCmd.Set) {
        logger.info("maxMessageSize is not currently wired in the nodes")
        val activeNetParams = networkMapStorage.getNetworkMaps().publicNetworkMap?.networkParameters?.networkParameters
        if (activeNetParams == null) {
            require(setNetParams.parametersUpdate == null) {
                "'parametersUpdate' specified in network parameters file but there are no network parameters to update"
            }
            val initialNetParams = setNetParams.toNetworkParameters(modifiedTime = Instant.now(), epoch = 1)
            logger.info("Saving initial network parameters to be signed:\n$initialNetParams")
            networkMapStorage.saveNetworkParameters(initialNetParams, null)
            println("Saved initial network parameters to be signed:\n$initialNetParams")
        } else {
            val parametersUpdate = requireNotNull(setNetParams.parametersUpdate) {
                "'parametersUpdate' not specified in network parameters file but there is already an active set of network parameters"
            }

            setNetParams.checkCompatibility(activeNetParams)

            val latestNetParams = checkNotNull(networkMapStorage.getLatestNetworkParameters()?.networkParameters) {
                "Something has gone wrong! We have an active set of network parameters ($activeNetParams) but apparently no latest network parameters!"
            }

            // It's not necessary that latestNetParams is the current active network parameters. It can be the network
            // parameters from a previous update attempt which has't activated yet. We still take the epoch value for this
            // new set from latestNetParams to make sure the advertised update attempts have incrementing epochs.
            // This has the implication that *active* network parameters may have gaps in their epochs.
            val newNetParams = setNetParams.toNetworkParameters(modifiedTime = Instant.now(), epoch = latestNetParams.epoch + 1)

            logger.info("Enabling update to network parameters:\n$newNetParams\n$parametersUpdate")

            require(!sameNetworkParameters(latestNetParams, newNetParams)) { "New network parameters are the same as the latest ones" }

            networkMapStorage.saveNewParametersUpdate(newNetParams, parametersUpdate.description, parametersUpdate.updateDeadline)

            logger.info("Update enabled")
            println("Enabled update to network parameters:\n$newNetParams\n$parametersUpdate")
        }
    }

    private fun sameNetworkParameters(params1: NetworkParameters, params2: NetworkParameters): Boolean {
        return params1.copy(epoch = 1, modifiedTime = Instant.MAX) == params2.copy(epoch = 1, modifiedTime = Instant.MAX)
    }

    private fun handleFlagDay() {
        val parametersUpdate = checkNotNull(networkMapStorage.getCurrentParametersUpdate()) {
            "No network parameters updates are scheduled"
        }
        check(Instant.now() >= parametersUpdate.updateDeadline) {
            "Update deadline of ${parametersUpdate.updateDeadline} hasn't passed yet"
        }
        val latestNetParamsEntity = networkMapStorage.getLatestNetworkParameters()
        check(parametersUpdate.networkParameters.hash == networkMapStorage.getLatestNetworkParameters()?.hash) {
            "The latest network parameters is not the scheduled one:\n${latestNetParamsEntity?.networkParameters}\n${parametersUpdate.toParametersUpdate()}"
        }
        val activeNetParams = networkMapStorage.getNetworkMaps().publicNetworkMap?.networkParameters
        check(parametersUpdate.networkParameters.isSigned) {
            "Parameters we are trying to switch to haven't been signed yet"
        }
        logger.info("""Flag day has occurred, however the new network parameters won't be active until the new network map is signed.
From: ${activeNetParams?.networkParameters}
To: ${parametersUpdate.networkParameters.networkParameters}""")
        networkMapStorage.setParametersUpdateStatus(parametersUpdate, UpdateStatus.FLAG_DAY)
    }

    private fun handleCancelUpdate() {
        val parametersUpdate = checkNotNull(networkMapStorage.getCurrentParametersUpdate()) {
            "No network parameters updates are scheduled"
        }
        logger.info("""Cancelling parameters update: ${parametersUpdate.toParametersUpdate()}.
However, the network map will continue to advertise this update until the new one is signed.""")
        networkMapStorage.setParametersUpdateStatus(parametersUpdate, UpdateStatus.CANCELLED)
        println("Done with cancel update")
    }
}