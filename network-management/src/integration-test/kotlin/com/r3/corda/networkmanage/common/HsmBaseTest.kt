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
import net.corda.core.internal.div
import net.corda.nodeapi.internal.crypto.CertificateType
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import java.util.*

abstract class HsmBaseTest {
    companion object {
        const val ROOT_CERT_KEY_GROUP = "DEV.CORDACONNECT.ROOT"
        const val NETWORK_MAP_CERT_KEY_GROUP = "DEV.CORDACONNECT.OPS.NETMAP"
        const val DOORMAN_CERT_KEY_GROUP = "DEV.CORDACONNECT.OPS.CERT"
        const val ROOT_CERT_SUBJECT = "CN=Corda Root CA, O=R3 HoldCo LLC, OU=Corda, L=New York, C=US"
        const val NETWORK_MAP_CERT_SUBJECT = "CN=Corda Network Map, O=R3 HoldCo LLC, OU=Corda, L=New York, C=US"
        const val DOORMAN_CERT_SUBJECT = "CN=Corda Doorman CA, O=R3 HoldCo LLC, OU=Corda, L=New York, C=US"
        val HSM_USER_CONFIGS = listOf(UserAuthenticationParameters(
                username = "INTEGRATION_TEST",
                authMode = AuthMode.PASSWORD,
                authToken = "INTEGRATION_TEST",
                keyFilePassword = null))
        const val ROOT_KEYSTORE_PASSWORD: String = "trustpass"
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
        rootKeyStoreFile =  tempFolder.root.toPath() / "truststore.jks"
        dbName = random63BitValue().toString()
    }

    private fun createGeneratorParameters(certConfig: CertificateConfiguration): GeneratorParameters {
        return GeneratorParameters(
                hsmHost = hsmSimulator.host,
                hsmPort = hsmSimulator.port,
                trustStoreDirectory = rootKeyStoreFile.parent,
                trustStorePassword = ROOT_KEYSTORE_PASSWORD,
                userConfigs = HSM_USER_CONFIGS,
                certConfig = certConfig
        )
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

    protected fun createHsmSigningServiceConfig(): Parameters {
        return Parameters(
                dataSourceProperties = mock(),
                device = "${hsmSimulator.port}@${hsmSimulator.host}",
                keySpecifier = 1,
                rootKeyStoreFile = rootKeyStoreFile,
                rootKeyStorePassword = ROOT_KEYSTORE_PASSWORD,
                doormanKeyGroup = DOORMAN_CERT_KEY_GROUP,
                networkMapKeyGroup = NETWORK_MAP_CERT_KEY_GROUP,
                validDays = 3650,
                csrCertCrlDistPoint = "http://test.com/revoked.crl"
        )
    }

    protected fun givenHsmUserAuthenticationInput(): InputReader {
        val inputReader = mock<InputReader>()
        whenever(inputReader.readLine()).thenReturn(hsmSimulator.cryptoUserCredentials().username)
        whenever(inputReader.readPassword(any())).thenReturn(hsmSimulator.cryptoUserCredentials().password)
        return inputReader
    }

    fun makeTestDataSourceProperties(): Properties {
        return makeTestDataSourceProperties(dbName)
    }
}