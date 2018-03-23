/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.common

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import com.r3.corda.networkmanage.HsmSimulator
import com.r3.corda.networkmanage.hsm.authentication.InputReader
import com.r3.corda.networkmanage.hsm.configuration.AuthParametersConfig
import com.r3.corda.networkmanage.hsm.configuration.DoormanCertificateConfig
import com.r3.corda.networkmanage.hsm.configuration.NetworkMapCertificateConfig
import com.r3.corda.networkmanage.hsm.configuration.SigningServiceConfig
import com.r3.corda.networkmanage.hsm.generator.CertificateConfiguration
import com.r3.corda.networkmanage.hsm.generator.GeneratorParameters
import com.r3.corda.networkmanage.hsm.generator.UserAuthenticationParameters
import net.corda.core.crypto.random63BitValue
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.crypto.CertificateType
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.net.URL
import java.nio.file.Path
import java.util.*
import com.r3.corda.networkmanage.hsm.authentication.AuthMode as SigningServiceAuthMode
import com.r3.corda.networkmanage.hsm.generator.AuthMode as GeneratorAuthMode

abstract class HsmBaseTest {
    companion object {
        const val ROOT_CERT_KEY_GROUP = "TEST.CORDACONNECT.ROOT"
        const val NETWORK_MAP_CERT_KEY_GROUP = "TEST.CORDACONNECT.OPS.NETMAP"
        const val DOORMAN_CERT_KEY_GROUP = "TEST.CORDACONNECT.OPS.CERT"
        const val ROOT_CERT_SUBJECT = "CN=Corda Root CA, O=R3 Ltd, OU=Corda, L=London, C=GB"
        const val NETWORK_MAP_CERT_SUBJECT = "CN=Corda Network Map, O=R3 Ltd, OU=Corda, L=London, C=GB"
        const val DOORMAN_CERT_SUBJECT = "CN=Corda Doorman CA, O=R3 Ltd, OU=Corda, L=London, C=GB"
        const val TRUSTSTORE_PASSWORD: String = "trustpass"
        const val HSM_USERNAME = "INTEGRATION_TEST"
        const val HSM_PASSWORD = "INTEGRATION_TEST"
        const val HSM_USERNAME_SUPER = "INTEGRATION_TEST_SUPER"
        const val HSM_USERNAME_OPS = "INTEGRATION_TEST_OPS"
        const val HSM_USERNAME_ROOT = "INTEGRATION_TEST_ROOT"
        const val HSM_USERNAME_SUPER_ = "INTEGRATION_TEST_SUPER_"
        const val HSM_USERNAME_OPS_ = "INTEGRATION_TEST_OPS_"
        const val HSM_USERNAME_ROOT_ = "INTEGRATION_TEST_ROOT_"
        const val HSM_USERNAME_OPS_CERT = "INTEGRATION_TEST_OPS_CERT"
        const val HSM_USERNAME_OPS_NETMAP = "INTEGRATION_TEST_OPS_NETMAP"
        const val HSM_USERNAME_OPS_CERT_ = "INTEGRATION_TEST_OPS_CERT_"
        const val HSM_USERNAME_OPS_NETMAP_ = "INTEGRATION_TEST_OPS_NETMAP_"
        val HSM_USER_CONFIGS = createHsmUserConfigs(HSM_USERNAME)
        val HSM_SUPER_USER_CONFIGS = createHsmUserConfigs(HSM_USERNAME_SUPER)
        val HSM_ROOT_USER_CONFIGS = createHsmUserConfigs(HSM_USERNAME_ROOT)
        val HSM_OPS_USER_CONFIGS = createHsmUserConfigs(HSM_USERNAME_OPS)
        val HSM_SUPER__USER_CONFIGS = createHsmUserConfigs(HSM_USERNAME_SUPER_)
        val HSM_ROOT__USER_CONFIGS = createHsmUserConfigs(HSM_USERNAME_ROOT_)
        val HSM_OPS__USER_CONFIGS = createHsmUserConfigs(HSM_USERNAME_OPS_)
        val HSM_OPS_CERT_USER_CONFIGS = createHsmUserConfigs(HSM_USERNAME_OPS_CERT)
        val HSM_OPS_NETMAP_USER_CONFIGS = createHsmUserConfigs(HSM_USERNAME_OPS_NETMAP)
        val HSM_OPS_CERT__USER_CONFIGS = createHsmUserConfigs(HSM_USERNAME_OPS_CERT_)
        val HSM_OPS_NETMAP__USER_CONFIGS = createHsmUserConfigs(HSM_USERNAME_OPS_NETMAP_)

        private fun createHsmUserConfigs(username: String): List<UserAuthenticationParameters> {
            return listOf(UserAuthenticationParameters(
                    username = username,
                    authMode = GeneratorAuthMode.PASSWORD,
                    authToken = "INTEGRATION_TEST",
                    keyFilePassword = null))
        }
    }

    protected lateinit var rootKeyStoreFile: Path

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Rule
    @JvmField
    val hsmSimulator: HsmSimulator = HsmSimulator()

    private lateinit var dbName: String

    @Before
    fun generateDbName() {
        rootKeyStoreFile = tempFolder.root.toPath() / "truststore.jks"
        dbName = random63BitValue().toString()
    }

    private fun createGeneratorParameters(certConfig: CertificateConfiguration,
                                          userConfigs: List<UserAuthenticationParameters>): GeneratorParameters {
        return GeneratorParameters(
                hsmHost = hsmSimulator.host,
                hsmPort = hsmSimulator.port,
                trustStoreDirectory = rootKeyStoreFile.parent,
                trustStorePassword = TRUSTSTORE_PASSWORD,
                userConfigs = userConfigs,
                certConfig = certConfig
        )
    }

    protected fun createGeneratorParameters(keyGroup: String,
                                            rootKeyGroup: String?,
                                            certificateType: CertificateType,
                                            subject: String,
                                            hsmUserConfigs: List<UserAuthenticationParameters> = HSM_USER_CONFIGS): GeneratorParameters {
        return createGeneratorParameters(CertificateConfiguration(
                keySpecifier = 1,
                keyGroup = keyGroup,
                storeKeysExternal = false,
                rootKeyGroup = rootKeyGroup,
                subject = subject,
                validDays = 3650,
                keyCurve = "NIST-P256",
                certificateType = certificateType,
                keyExport = 0,
                keyGenMechanism = 4,
                keyOverride = 0,
                crlIssuer = null,
                crlDistributionUrl = null
        ), hsmUserConfigs)
    }

    protected fun createHsmSigningServiceConfig(doormanCertConfig: DoormanCertificateConfig?,
                                                networkMapCertificateConfig: NetworkMapCertificateConfig?): SigningServiceConfig {
        return SigningServiceConfig(
                dataSourceProperties = mock(),
                device = "${hsmSimulator.port}@${hsmSimulator.host}",
                keySpecifier = 1,
                doorman = doormanCertConfig,
                networkMap = networkMapCertificateConfig
        )
    }

    protected fun createDoormanCertificateConfig(): DoormanCertificateConfig {
        return DoormanCertificateConfig(
                rootKeyStoreFile = rootKeyStoreFile,
                keyGroup = DOORMAN_CERT_KEY_GROUP,
                validDays = 3650,
                rootKeyStorePassword = TRUSTSTORE_PASSWORD,
                crlDistributionPoint = URL("http://test.com/revoked.crl"),
                crlServerSocketAddress = NetworkHostAndPort("test.com", 4555),
                crlUpdatePeriod = 1000,
                authParameters = AuthParametersConfig(
                        mode = SigningServiceAuthMode.PASSWORD,
                        threshold = 2
                )
        )
    }

    protected fun createNetworkMapCertificateConfig(): NetworkMapCertificateConfig {
        return NetworkMapCertificateConfig(
                username = "INTEGRATION_TEST",
                keyGroup = NETWORK_MAP_CERT_KEY_GROUP,
                authParameters = AuthParametersConfig(
                        mode = SigningServiceAuthMode.PASSWORD,
                        password = "INTEGRATION_TEST",
                        threshold = 2
                )

        )
    }

    protected fun givenHsmUserAuthenticationInput(username: String = HSM_USERNAME,
                                                  password: String = HSM_PASSWORD): InputReader {
        val inputReader = mock<InputReader>()
        whenever(inputReader.readLine()).thenReturn(username)
        whenever(inputReader.readPassword(any())).thenReturn(password)
        return inputReader
    }

    fun makeTestDataSourceProperties(): Properties {
        return makeTestDataSourceProperties(dbName)
    }
}