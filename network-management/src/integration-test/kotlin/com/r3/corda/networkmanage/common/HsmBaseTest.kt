package com.r3.corda.networkmanage.common

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import com.r3.corda.networkmanage.HsmSimulator
import com.r3.corda.networkmanage.hsm.authentication.InputReader
import com.r3.corda.networkmanage.hsm.configuration.Parameters
import com.r3.corda.networkmanage.hsm.generator.AuthMode
import com.r3.corda.networkmanage.hsm.generator.CertificateConfiguration
import com.r3.corda.networkmanage.hsm.generator.GeneratorParameters
import com.r3.corda.networkmanage.hsm.generator.UserAuthenticationParameters
import net.corda.core.crypto.random63BitValue
import net.corda.nodeapi.internal.crypto.CertificateType
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.util.*

abstract class HsmBaseTest {
    companion object {
        val ROOT_CERT_KEY_GROUP = "DEV.CORDACONNECT.ROOT"
        val NETWORK_MAP_CERT_KEY_GROUP = "DEV.CORDACONNECT.OPS.NETMAP"
        val DOORMAN_CERT_KEY_GROUP = "DEV.CORDACONNECT.OPS.CERT"
        val ROOT_CERT_SUBJECT = "CN=Corda Root CA, O=R3 HoldCo LLC, OU=Corda, L=New York, C=US"
        val NETWORK_MAP_CERT_SUBJECT = "CN=Corda Network Map, O=R3 HoldCo LLC, OU=Corda, L=New York, C=US"
        val DOORMAN_CERT_SUBJECT = "CN=Corda Doorman CA, O=R3 HoldCo LLC, OU=Corda, L=New York, C=US"
        val HSM_USER_CONFIGS = listOf(UserAuthenticationParameters(
                username = "INTEGRATION_TEST",
                authMode = AuthMode.PASSWORD,
                authToken = "INTEGRATION_TEST",
                keyFilePassword = null))
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Rule
    @JvmField
    val hsmSimulator: HsmSimulator = HsmSimulator()

    private fun createGeneratorParameters(certConfig: CertificateConfiguration): GeneratorParameters {
        return GeneratorParameters(
                hsmHost = hsmSimulator.host,
                hsmPort = hsmSimulator.port,
                trustStoreDirectory = tempFolder.root.toPath(),
                trustStorePassword = "",
                userConfigs = HSM_USER_CONFIGS,
                certConfig = certConfig
        )
    }

    protected lateinit var dbName: String

    @Before
    fun generateDbName() {
        dbName = random63BitValue().toString()
    }

    protected fun createGeneratorParameters(keyGroup: String,
                                            rootKeyGroup: String?,
                                            certificateType: CertificateType,
                                            subject: String): GeneratorParameters {
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
        ))
    }

    protected val hsmSigningServiceConfig = Parameters(
            dataSourceProperties = mock(),
            device = "${hsmSimulator.port}@${hsmSimulator.host}",
            keySpecifier = 1,
            rootKeyGroup = ROOT_CERT_KEY_GROUP,
            doormanKeyGroup = DOORMAN_CERT_KEY_GROUP,
            networkMapKeyGroup = NETWORK_MAP_CERT_KEY_GROUP,
            validDays = 3650,
            csrCertCrlDistPoint = "http://test.com/revoked.crl"
    )

    protected fun givenHsmUserAuthenticationInput(): InputReader {
        val inputReader = mock<InputReader>()
        whenever(inputReader.readLine()).thenReturn(hsmSimulator.cryptoUserCredentials().username)
        whenever(inputReader.readPassword(any())).thenReturn(hsmSimulator.cryptoUserCredentials().password)
        return inputReader
    }

    protected fun makeTestDataSourceProperties(): Properties {
        return makeTestDataSourceProperties(dbName)
    }
}