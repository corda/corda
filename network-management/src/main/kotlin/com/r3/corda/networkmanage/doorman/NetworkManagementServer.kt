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
import com.r3.corda.networkmanage.common.signer.NetworkMapSigner
import com.r3.corda.networkmanage.common.utils.CertPathAndKey
import com.r3.corda.networkmanage.doorman.signer.*
import com.r3.corda.networkmanage.doorman.webservice.*
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import org.apache.commons.io.FileUtils
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
    private val csrStorage = PersistentCertificateSigningRequestStorage(database).let {
        if (doormanConfig?.approveAll ?: false) {
            ApproveAllCertificateSigningRequestStorage(it)
        } else {
            it
        }
    }
    val netParamsUpdateHandler = ParametersUpdateHandler(csrStorage, networkMapStorage)

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

        val jiraConfig = config.jira
        val requestProcessor = if (jiraConfig != null) {
            val jiraWebAPI = AsynchronousJiraRestClientFactory().createWithBasicHttpAuthentication(URI(jiraConfig.address), jiraConfig.username, jiraConfig.password)
            val jiraClient = CsrJiraClient(jiraWebAPI, jiraConfig.projectCode)
            JiraCsrHandler(jiraClient, csrStorage, DefaultCsrHandler(csrStorage, csrCertPathAndKey, config.crlEndpoint))
        } else {
            DefaultCsrHandler(csrStorage, csrCertPathAndKey, config.crlEndpoint)
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
                    CertificateAndKeyPair(it.certPath[0], it.toKeyPair()),
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
        return Pair(
                CertificateRevocationRequestWebService(crrHandler),
                CertificateRevocationListWebService(
                        crlStorage,
                        FileUtils.readFileToByteArray(config.caCrlPath.toFile()),
                        FileUtils.readFileToByteArray(config.emptyCrlPath.toFile()),
                        Duration.ofMillis(config.crlCacheTimeout)))
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
}
