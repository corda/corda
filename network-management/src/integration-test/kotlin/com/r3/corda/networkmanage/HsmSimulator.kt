/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage

import CryptoServerAPI.CryptoServerException
import com.r3.corda.networkmanage.hsm.authentication.CryptoServerProviderConfig
import com.r3.corda.networkmanage.hsm.authentication.createProvider
import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.messages.ContainerConfig
import com.spotify.docker.client.messages.HostConfig
import com.spotify.docker.client.messages.PortBinding
import com.spotify.docker.client.messages.RegistryAuth
import net.corda.core.utilities.loggerFor
import net.corda.testing.core.freeLocalHostAndPort
import org.junit.Assume.assumeFalse
import org.junit.rules.ExternalResource

data class CryptoUserCredentials(val username: String, val password: String)

/**
 * HSM Simulator rule allowing to use the HSM Simulator within the integration tests. It is designed to be used mainly
 * on the TeamCity, but if the required setup is available it can be executed locally as well.
 * It will bind to the simulator to the local port
 * To use it locally, the following pre-requisites need to be met:
 * 1) Docker engine needs to be installed on the machine
 * 2) Environment variables (AZURE_CR_USER and AZURE_CR_PASS) are available and hold valid credentials to the corda.azurecr.io
 * repository
 * 3) HSM requires Unlimited Strength Jurisdiction extension to be installed on the machine connecting with the HSM box.
 * See http://www.oracle.com/technetwork/java/javase/downloads/jce8-download-2133166.html
 *
 * Since the above setup is not a strong requirement for the integration tests to be executed it is intended that this
 * rule is used together with the assumption mechanism in tests.
 */
class HsmSimulator(private val serverAddress: String = DEFAULT_SERVER_ADDRESS,
                   private val imageRepoTag: String = DEFAULT_IMAGE_REPO_TAG,
                   private val imageVersion: String = DEFAULT_IMAGE_VERSION,
                   private val pullImage: Boolean = DEFAULT_PULL_IMAGE,
                   private val registryUser: String? = REGISTRY_USERNAME,
                   private val registryPass: String? = REGISTRY_PASSWORD) : ExternalResource() {

    private companion object {
        val DEFAULT_SERVER_ADDRESS = "corda.azurecr.io"
        /*
         * Currently we have following images:
         * 1) corda.azurecr.io/network-management/hsm-simulator - having only one user configured:
         *    - INTEGRATION_TEST (password: INTEGRATION_TEST) with the CXI_GROUP="*"
         * 2)corda.azurecr.io/network-management/hsm-simulator-with-groups - having following users configured:
         *    - INTEGRATION_TEST (password: INTEGRATION_TEST) with the CXI_GROUP=*
         *    - INTEGRATION_TEST_SUPER (password: INTEGRATION_TEST) with the CXI_GROUP=TEST.CORDACONNECT
         *    - INTEGRATION_TEST_ROOT (password: INTEGRATION_TEST) with the CXI_GROUP=TEST.CORDACONNECT.ROOT
         *    - INTEGRATION_TEST_OPS (password: INTEGRATION_TEST) with the CXI_GROUP=TEST.CORDACONNECT.OPS
         *    - INTEGRATION_TEST_SUPER_ (password: INTEGRATION_TEST) with the CXI_GROUP=TEST.CORDACONNECT.*
         *    - INTEGRATION_TEST_ROOT_ (password: INTEGRATION_TEST) with the CXI_GROUP=TEST.CORDACONNECT.ROOT.*
         *    - INTEGRATION_TEST_OPS_ (password: INTEGRATION_TEST) with the CXI_GROUP=TEST.CORDACONNECT.OPS.*
         *    - INTEGRATION_TEST_OPS_CERT (password: INTEGRATION_TEST) with the CXI_GROUP=TEST.CORDACONNECT.OPS.CERT
         *    - INTEGRATION_TEST_OPS_NETMAP (password: INTEGRATION_TEST) with the CXI_GROUP=TEST.CORDACONNECT.OPS.NETMAP
         *    - INTEGRATION_TEST_OPS_CERT (password: INTEGRATION_TEST) with the CXI_GROUP=TEST.CORDACONNECT.OPS.CERT.*
         *    - INTEGRATION_TEST_OPS_NETMAP (password: INTEGRATION_TEST) with the CXI_GROUP=TEST.CORDACONNECT.OPS.NETMAP.*
         */
        val DEFAULT_IMAGE_REPO_TAG = "corda.azurecr.io/network-management/hsm-simulator-with-groups"
        val DEFAULT_IMAGE_VERSION = "latest"
        val DEFAULT_PULL_IMAGE = true

        val HSM_SIMULATOR_PORT = "3001/tcp"
        val CONTAINER_KILL_TIMEOUT_SECONDS = 10

        val CRYPTO_USER = System.getProperty("CRYPTO_USER", "INTEGRATION_TEST")
        val CRYPTO_PASSWORD = System.getProperty("CRYPTO_PASSWORD", "INTEGRATION_TEST")

        val REGISTRY_USERNAME = System.getenv("AZURE_CR_USER")
        val REGISTRY_PASSWORD = System.getenv("AZURE_CR_PASS")

        val log = loggerFor<HsmSimulator>()

        private val HSM_STARTUP_SLEEP_INTERVAL_MS = 500L
        private val HSM_STARTUP_POLL_MAX_COUNT = 10;
    }

    private val localHostAndPortBinding = freeLocalHostAndPort()
    private lateinit var docker: DockerClient
    private var containerId: String? = null

    override fun before() {
        assumeFalse("Docker registry username is not set!. Skipping the test.", registryUser.isNullOrBlank())
        assumeFalse("Docker registry password is not set!. Skipping the test.", registryPass.isNullOrBlank())
        docker = DefaultDockerClient.fromEnv().build()
        if (pullImage) {
            docker.pullHsmSimulatorImageFromRepository()
        }
        containerId = docker.createContainer()
        docker.startHsmSimulatorContainer()
    }

    override fun after() {
        docker.stopAndRemoveHsmSimulatorContainer()
    }

    /**
     * Retrieves the port at which simulator is listening at.
     */
    val port get(): Int = localHostAndPortBinding.port

    /**
     * Retrieves the host IP address, which the simulator is listening at.
     */
    val host get(): String = localHostAndPortBinding.host

    /**
     * Retrieves the HSM user credentials. Those are supposed to be preconfigured on the HSM itself. Thus, when
     * tests are executed these credentials can be used to access HSM crypto user's functionality.
     * It is assumed that the docker image state has those configured already and they should match the ones returned.
     */
    fun cryptoUserCredentials(): CryptoUserCredentials {
        return CryptoUserCredentials(CRYPTO_USER, CRYPTO_PASSWORD)
    }

    private fun DockerClient.stopAndRemoveHsmSimulatorContainer() {
        if (containerId != null) {
            log.debug("Stopping container $containerId...")
            this.stopContainer(containerId, CONTAINER_KILL_TIMEOUT_SECONDS)
            log.debug("Removing container $containerId...")
            this.removeContainer(containerId)
        }
    }

    private fun DockerClient.startHsmSimulatorContainer() {
        if (containerId != null) {
            log.debug("Starting container $containerId...")
            this.startContainer(containerId)
            pollAndWaitForHsmSimulator()
        }
    }

    private fun pollAndWaitForHsmSimulator() {
        val config = CryptoServerProviderConfig(
                Device = "${localHostAndPortBinding.port}@${localHostAndPortBinding.host}",
                KeyGroup = "*",
                KeySpecifier = -1
        )
        var pollCount = HSM_STARTUP_POLL_MAX_COUNT
        while (pollCount > 0) {
            val provider = createProvider(config)
            try {
                provider.loginPassword(CRYPTO_USER, CRYPTO_PASSWORD)
                provider.cryptoServer.authState
                return
            } catch (e: CryptoServerException) {
                pollCount--
                Thread.sleep(HSM_STARTUP_SLEEP_INTERVAL_MS)
            } finally {
                provider.logoff()
            }
        }
        throw IllegalStateException("Unable to obtain connection to initialised HSM Simulator")
    }

    private fun getImageFullName() = "$imageRepoTag:$imageVersion"

    private fun DockerClient.pullHsmSimulatorImageFromRepository(): DockerClient {
        this.pull(imageRepoTag,
                RegistryAuth.builder()
                        .serverAddress(serverAddress)
                        .username(registryUser)
                        .password(registryPass)
                        .build())
        return this
    }

    private fun DockerClient.createContainer(): String? {
        val portBindings = mapOf(HSM_SIMULATOR_PORT to listOf(PortBinding.create(localHostAndPortBinding.host, localHostAndPortBinding.port.toString())))
        val hostConfig = HostConfig.builder().portBindings(portBindings).build()
        val containerConfig = ContainerConfig.builder()
                .hostConfig(hostConfig)
                .portSpecs()
                .image(getImageFullName())
                .exposedPorts(HSM_SIMULATOR_PORT)
                .build()
        val containerCreation = this.createContainer(containerConfig)
        return containerCreation.id()
    }
}