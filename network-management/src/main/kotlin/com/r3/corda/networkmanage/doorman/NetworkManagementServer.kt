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
import com.r3.corda.networkmanage.common.persistence.ApproveAllCertificateSigningRequestStorage
import com.r3.corda.networkmanage.common.persistence.PersistentCertificateSigningRequestStorage
import com.r3.corda.networkmanage.common.persistence.PersistentNetworkMapStorage
import com.r3.corda.networkmanage.common.persistence.PersistentNodeInfoStorage
import com.r3.corda.networkmanage.common.signer.NetworkMapSigner
import com.r3.corda.networkmanage.common.utils.CertPathAndKey
import com.r3.corda.networkmanage.doorman.signer.DefaultCsrHandler
import com.r3.corda.networkmanage.doorman.signer.JiraCsrHandler
import com.r3.corda.networkmanage.doorman.signer.LocalSigner
import com.r3.corda.networkmanage.doorman.webservice.MonitoringWebService
import com.r3.corda.networkmanage.doorman.webservice.NetworkMapWebService
import com.r3.corda.networkmanage.doorman.webservice.RegistrationWebService
import net.corda.core.node.NetworkParameters
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.persistence.CordaPersistence
import java.io.Closeable
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NetworkManagementServer : Closeable {
    companion object {
        private val logger = contextLogger()
    }

    private val closeActions = mutableListOf<() -> Unit>()
    lateinit var hostAndPort: NetworkHostAndPort

    override fun close() {
        for (closeAction in closeActions) {
            try {
                closeAction()
            } catch (e: Exception) {
                logger.warn("Disregarding exception thrown during close", e)
            }
        }
    }

    private fun getNetworkMapService(config: NetworkMapConfig, database: CordaPersistence, signer: LocalSigner?, newNetworkParameters: NetworkParameters?): NetworkMapWebService {
        val networkMapStorage = PersistentNetworkMapStorage(database)
        val nodeInfoStorage = PersistentNodeInfoStorage(database)
        val localNetworkMapSigner = signer?.let { NetworkMapSigner(networkMapStorage, it) }

        newNetworkParameters?.let {
            val netParamsOfNetworkMap = networkMapStorage.getNetworkParametersOfNetworkMap()
            if (netParamsOfNetworkMap == null) {
                localNetworkMapSigner?.persistSignedNetworkParameters(it) ?: networkMapStorage.saveNetworkParameters(it, null)
            } else {
                throw UnsupportedOperationException("Network parameters already exist. Updating them is not supported yet.")
            }
        }

        val latestParameters = networkMapStorage.getLatestNetworkParameters() ?:
                throw IllegalStateException("No network parameters were found. Please upload new network parameters before starting network map service")
        logger.info("Starting network map service with network parameters: $latestParameters")

        if (localNetworkMapSigner != null) {
            logger.info("Starting background worker for signing the network map using the local key store")
            val scheduledExecutor = Executors.newScheduledThreadPool(1)
            scheduledExecutor.scheduleAtFixedRate({
                try {
                    localNetworkMapSigner.signNetworkMap()
                } catch (e: Exception) {
                    // Log the error and carry on.
                    logger.error("Unable to sign network map", e)
                }
            }, config.signInterval, config.signInterval, TimeUnit.MILLISECONDS)
            closeActions += scheduledExecutor::shutdown
        }

        return NetworkMapWebService(nodeInfoStorage, networkMapStorage, config)
    }


    private fun getDoormanService(config: DoormanConfig,
                                  database: CordaPersistence,
                                  csrCertPathAndKey: CertPathAndKey?,
                                  serverStatus: NetworkManagementServerStatus): RegistrationWebService {
        logger.info("Starting Doorman server.")
        val requestService = if (config.approveAll) {
            require(config.jira == null) { "Jira configuration cannot be specified when the approveAll parameter is set to true." }
            logger.warn("Doorman server is in 'Approve All' mode, this will approve all incoming certificate signing requests.")
            ApproveAllCertificateSigningRequestStorage(PersistentCertificateSigningRequestStorage(database))
        } else {
            PersistentCertificateSigningRequestStorage(database)
        }

        val jiraConfig = config.jira
        val requestProcessor = if (jiraConfig != null) {
            val jiraWebAPI = AsynchronousJiraRestClientFactory().createWithBasicHttpAuthentication(URI(jiraConfig.address), jiraConfig.username, jiraConfig.password)
            val jiraClient = JiraClient(jiraWebAPI, jiraConfig.projectCode)
            JiraCsrHandler(jiraClient, requestService, DefaultCsrHandler(requestService, csrCertPathAndKey))
        } else {
            DefaultCsrHandler(requestService, csrCertPathAndKey)
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

    fun start(hostAndPort: NetworkHostAndPort,
              database: CordaPersistence,
              csrCertPathAndKey: CertPathAndKey?,
              doormanServiceParameter: DoormanConfig?,  // TODO Doorman config shouldn't be optional as the doorman is always required to run
              startNetworkMap: NetworkMapStartParams?
    ) {
        val services = mutableListOf<Any>()
        val serverStatus = NetworkManagementServerStatus()

        startNetworkMap?.let { services += getNetworkMapService(it.config, database, it.signer, it.updateNetworkParameters) }
        doormanServiceParameter?.let { services += getDoormanService(it, database, csrCertPathAndKey, serverStatus) }

        require(services.isNotEmpty()) { "No service created, please provide at least one service config." }

        // TODO: use mbean to expose audit data?
        services += MonitoringWebService(serverStatus)

        val webServer = NetworkManagementWebServer(hostAndPort, *services.toTypedArray())
        webServer.start()

        closeActions += webServer::close
        this.hostAndPort = webServer.hostAndPort
    }
}