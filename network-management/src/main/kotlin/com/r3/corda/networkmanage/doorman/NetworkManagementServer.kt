/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.doorman

import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory
import com.r3.corda.networkmanage.common.persistence.*
import com.r3.corda.networkmanage.common.persistence.entity.UpdateStatus
import com.r3.corda.networkmanage.common.signer.NetworkMapSigner
import com.r3.corda.networkmanage.common.utils.CertPathAndKey
import com.r3.corda.networkmanage.doorman.signer.*
import com.r3.corda.networkmanage.doorman.webservice.*
import net.corda.core.node.NetworkParameters
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import java.io.Closeable
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NetworkManagementServer(dataSourceProperties: Properties,
                              databaseConfig: DatabaseConfig,
                              private val doormanConfig: DoormanConfig?, // TODO Doorman config shouldn't be optional as the doorman is always required to run
                              private val revocationConfig: CertificateRevocationConfig?) : Closeable {
    companion object {
        private val logger = contextLogger()
    }

    private val closeActions = mutableListOf<() -> Unit>()
    private val database = configureDatabase(dataSourceProperties, databaseConfig).also { closeActions += it::close }
    private val networkMapStorage = PersistentNetworkMapStorage(database)
    private val nodeInfoStorage = PersistentNodeInfoStorage(database)

    lateinit var hostAndPort: NetworkHostAndPort

    override fun close() {
        logger.info("Closing server...")
        for (closeAction in closeActions.reversed()) {
            try {
                closeAction()
            } catch (e: Exception) {
                logger.warn("Disregarding exception thrown during close", e)
            }
        }
    }

    private fun getNetworkMapService(config: NetworkMapConfig, signer: LocalSigner?): NetworkMapWebService {
        logger.info("Starting Network Map server.")
        val localNetworkMapSigner = signer?.let { NetworkMapSigner(networkMapStorage, it) }
        val latestParameters = networkMapStorage.getLatestNetworkParameters()?.networkParameters ?: throw IllegalStateException("No network parameters were found. Please upload new network parameters before starting network map service")
        logger.info("Starting network map service with latest network parameters: $latestParameters")
        localNetworkMapSigner?.signAndPersistNetworkParameters(latestParameters)

        if (localNetworkMapSigner != null) {
            logger.info("Starting background worker for signing the network map using the local key store")
            val scheduledExecutor = Executors.newScheduledThreadPool(1)
            scheduledExecutor.scheduleAtFixedRate({
                try {
                    localNetworkMapSigner.signNetworkMaps()
                } catch (e: Exception) {
                    // Log the error and carry on.
                    logger.error("Unable to sign network map", e)
                }
            }, config.signInterval, config.signInterval, TimeUnit.MILLISECONDS)
            closeActions += scheduledExecutor::shutdown
        }
        return NetworkMapWebService(nodeInfoStorage, networkMapStorage, PersistentCertificateSigningRequestStorage(database), config)
    }

    private fun getDoormanService(config: DoormanConfig,
                                  csrCertPathAndKey: CertPathAndKey?,
                                  serverStatus: NetworkManagementServerStatus): RegistrationWebService {
        logger.info("Starting Doorman server.")

        val csrStorage = PersistentCertificateSigningRequestStorage(database).let {
            if (config.approveAll) {
                ApproveAllCertificateSigningRequestStorage(it)
            } else {
                it
            }
        }

        val jiraConfig = config.jira
        val requestProcessor = if (jiraConfig != null) {
            val jiraWebAPI = AsynchronousJiraRestClientFactory().createWithBasicHttpAuthentication(URI(jiraConfig.address), jiraConfig.username, jiraConfig.password)
            val jiraClient = CsrJiraClient(jiraWebAPI, jiraConfig.projectCode)
            JiraCsrHandler(jiraClient, csrStorage, DefaultCsrHandler(csrStorage, csrCertPathAndKey))
        } else {
            DefaultCsrHandler(csrStorage, csrCertPathAndKey)
        }

        val scheduledExecutor = Executors.newScheduledThreadPool(1)
        val approvalThread = Runnable {
            try {
                serverStatus.lastRequestCheckTime = Instant.now()
                // Process Jira approved tickets.
                requestProcessor.processRequests()
            } catch (e: Exception) {
                // Log the error and carry on.
                logger.error("Error encountered when approving request.", e)
            }
        }
        scheduledExecutor.scheduleAtFixedRate(approvalThread, config.approveInterval, config.approveInterval, TimeUnit.MILLISECONDS)
        closeActions += scheduledExecutor::shutdown

        return RegistrationWebService(requestProcessor, Duration.ofMillis(config.approveInterval))
    }

    private fun getRevocationServices(config: CertificateRevocationConfig,
                                      csrCertPathAndKeyPair: CertPathAndKey?): Pair<CertificateRevocationRequestWebService, CertificateRevocationListWebService> {
        logger.info("Starting Revocation server.")

        val crrStorage = PersistentCertificateRevocationRequestStorage(database).let {
            if (config.approveAll) {
                ApproveAllCertificateRevocationRequestStorage(it)
            } else {
                it
            }
        }
        val crlStorage = PersistentCertificateRevocationListStorage(database)
        val crlHandler = csrCertPathAndKeyPair?.let {
            LocalCrlHandler(crrStorage,
                    crlStorage,
                    CertificateAndKeyPair(it.certPath.first(), it.toKeyPair()),
                    Duration.ofMillis(config.localSigning!!.crlUpdateInterval),
                    config.localSigning.crlEndpoint)
        }

        val jiraConfig = config.jira
        val crrHandler = if (jiraConfig != null) {
            val jiraWebAPI = AsynchronousJiraRestClientFactory().createWithBasicHttpAuthentication(URI(jiraConfig.address), jiraConfig.username, jiraConfig.password)
            val jiraClient = CrrJiraClient(jiraWebAPI, jiraConfig.projectCode)
            JiraCrrHandler(jiraClient, crrStorage, crlHandler)
        } else {
            DefaultCrrHandler(crrStorage, crlHandler)
        }

        val scheduledExecutor = Executors.newScheduledThreadPool(1)
        val approvalThread = Runnable {
            try {
                // Process Jira approved tickets.
                crrHandler.processRequests()
            } catch (e: Exception) {
                // Log the error and carry on.
                logger.error("Error encountered when approving request.", e)
            }
        }
        scheduledExecutor.scheduleAtFixedRate(approvalThread, config.approveInterval, config.approveInterval, TimeUnit.MILLISECONDS)
        closeActions += scheduledExecutor::shutdown
        // TODO start socket server
        return Pair(CertificateRevocationRequestWebService(crrHandler), CertificateRevocationListWebService(crlStorage, Duration.ofMillis(config.crlCacheTimeout)))
    }

    fun start(hostAndPort: NetworkHostAndPort,
              csrCertPathAndKey: CertPathAndKey?,
              startNetworkMap: NetworkMapStartParams?
    ) {
        val services = mutableListOf<Any>()
        val serverStatus = NetworkManagementServerStatus()

        startNetworkMap?.let { services += getNetworkMapService(it.config, it.signer) }
        doormanConfig?.let { services += getDoormanService(it, csrCertPathAndKey, serverStatus) }
        revocationConfig?.let {
            val revocationServices = getRevocationServices(it, csrCertPathAndKey)
            services += revocationServices.first
            services += revocationServices.second
        }

        require(services.isNotEmpty()) { "No service created, please provide at least one service config." }

        // TODO: use mbean to expose audit data?
        services += MonitoringWebService(serverStatus)

        val webServer = NetworkManagementWebServer(hostAndPort, *services.toTypedArray())
        webServer.start()

        closeActions += webServer::close
        this.hostAndPort = webServer.hostAndPort
    }

    fun processNetworkParameters(networkParametersCmd: NetworkParametersCmd) {
        when (networkParametersCmd) {
            is NetworkParametersCmd.Set -> handleSetNetworkParameters(networkParametersCmd)
            NetworkParametersCmd.FlagDay -> handleFlagDay()
            NetworkParametersCmd.CancelUpdate -> handleCancelUpdate()
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
