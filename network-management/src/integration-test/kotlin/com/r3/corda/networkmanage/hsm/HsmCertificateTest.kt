package com.r3.corda.networkmanage.hsm

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import com.r3.corda.networkmanage.HsmSimulator
import com.r3.corda.networkmanage.hsm.authentication.CryptoServerProviderConfig
import com.r3.corda.networkmanage.hsm.authentication.InputReader
import com.r3.corda.networkmanage.hsm.configuration.Parameters
import com.r3.corda.networkmanage.hsm.generator.AuthMode
import com.r3.corda.networkmanage.hsm.generator.CertificateConfiguration
import com.r3.corda.networkmanage.hsm.generator.GeneratorParameters
import com.r3.corda.networkmanage.hsm.generator.UserAuthenticationParameters
import net.corda.nodeapi.internal.crypto.CertificateType
import org.junit.Rule
import org.junit.rules.TemporaryFolder

abstract class HsmCertificateTest {
    companion object {
        val ROOT_CERT_KEY_GROUP = "DEV.CORDACONNECT.ROOT"
        val NETWORK_MAP_CERT_KEY_GROUP = "DEV.CORDACONNECT.OPS.NETMAP"
        val DOORMAN_CERT_KEY_GROUP = "DEV.CORDACONNECT.OPS.CERT"
        val ROOT_CERT_SUBJECT = "CN=Corda Root CA, O=R3 HoldCo LLC, OU=Corda, L=New York, C=US"
        val NETWORK_MAP_CERT_SUBJECT = "CN=Corda Network Map, O=R3 HoldCo LLC, OU=Corda, L=New York, C=US"
        val DOORMAN_CERT_SUBJECT = "CN=Corda Doorman CA, O=R3 HoldCo LLC, OU=Corda, L=New York, C=US"
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Rule
    @JvmField
    val hsmSimulator: HsmSimulator = HsmSimulator()

    protected val rootCertParameters: GeneratorParameters by lazy {
        GeneratorParameters(
                hsmHost = hsmSimulator.host,
                hsmPort = hsmSimulator.port,
                trustStoreDirectory = tempFolder.root.toPath(),
                trustStorePassword = "",
                userConfigs = listOf(UserAuthenticationParameters(
                        username = "INTEGRATION_TEST",
                        authMode = AuthMode.PASSWORD,
                        authToken = "INTEGRATION_TEST",
                        keyFilePassword = null
                )),
                certConfig = CertificateConfiguration(
                        keySpecifier = 1,
                        keyGroup = ROOT_CERT_KEY_GROUP,
                        storeKeysExternal = false,
                        rootKeyGroup = null,
                        subject = ROOT_CERT_SUBJECT,
                        validDays = 3650,
                        keyCurve = "NIST-P256",
                        certificateType = CertificateType.ROOT_CA,
                        keyExport = 0,
                        keyGenMechanism = 4,
                        keyOverride = 0,
                        crlIssuer = null,
                        crlDistributionUrl = null
                )
        )
    }

    protected val providerConfig: CryptoServerProviderConfig by lazy {
        CryptoServerProviderConfig(
                Device = "${rootCertParameters.hsmPort}@${rootCertParameters.hsmHost}",
                KeySpecifier = rootCertParameters.certConfig.keySpecifier,
                KeyGroup = rootCertParameters.certConfig.keyGroup,
                StoreKeysExternal = rootCertParameters.certConfig.storeKeysExternal)
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
}