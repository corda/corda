package net.corda.node.services.network

import com.nhaarman.mockito_kotlin.verify
import net.corda.core.identity.Party
import net.corda.core.internal.NetworkParametersStorage
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.core.serialization.serialize
import net.corda.coretesting.internal.DEV_ROOT_CA
import net.corda.node.internal.NetworkParametersReader
import net.corda.nodeapi.internal.createDevNetworkMapCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.testing.common.internal.addNotary
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class NetworkParametersHotloaderTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)
    val networkMapCertAndKeyPair: CertificateAndKeyPair = createDevNetworkMapCa()
    val trustRoot = DEV_ROOT_CA.certificate

    val originalNetworkParameters = testNetworkParameters()
    val notary: Party = TestIdentity.fresh("test notary").party
    val networkParametersWithNotary = originalNetworkParameters.addNotary(notary)
    val networkParametersStorage = Mockito.mock(NetworkParametersStorage::class.java)

    @Test(timeout = 300_000)
    fun `listener function gets invoked if parameter changes are hotloadable`() {

        val notaryUpdateListener = Mockito.spy(object : NotaryUpdateListener {
            override fun onNewNotaryList(notaries: List<NotaryInfo>) {
            }
        })

        val networkParametersChangedListener = Mockito.spy(object : NetworkParameterUpdateListener {
            override fun onNewNetworkParameters(networkParameters: NetworkParameters) {
            }
        })

        val networkParametersHotloader = createHotloaderWithMockedServices(networkParametersWithNotary)
        networkParametersHotloader.addNotaryUpdateListener(notaryUpdateListener)
        networkParametersHotloader.addNetworkParametersChangedListeners(networkParametersChangedListener)

        networkParametersHotloader.attemptHotload(networkParametersWithNotary.serialize().hash)
        verify(notaryUpdateListener).onNewNotaryList(networkParametersWithNotary.notaries)
        verify(networkParametersChangedListener).onNewNetworkParameters(networkParametersWithNotary)
    }

    @Test(timeout = 300_000)
    fun `can not hotload if notary changes but no listener function exists`() {

        val networkParametersHotloader = createHotloaderWithMockedServices(networkParametersWithNotary)
        Assert.assertFalse(networkParametersHotloader.attemptHotload(networkParametersWithNotary.serialize().hash))
    }

    @Test(timeout = 300_000)
    fun `can hotload if notary changes`() {
        `can hotload`(networkParametersWithNotary)
    }

    @Test(timeout = 300_000)
    fun `can not hotload if notary changes but another non-hotloadable property also changes`() {

        val newnetParamsWithNewNotaryAndMaxMsgSize = networkParametersWithNotary.copy(maxMessageSize = networkParametersWithNotary.maxMessageSize + 1)
        `can not hotload`(newnetParamsWithNewNotaryAndMaxMsgSize)
    }

    @Test(timeout = 300_000)
    fun `can hotload if only always hotloadable properties change`() {

        val newParametersWithAlwaysHotloadableProperties = originalNetworkParameters.copy(epoch = originalNetworkParameters.epoch + 1, modifiedTime = originalNetworkParameters.modifiedTime.plusSeconds(60))
        `can hotload`(newParametersWithAlwaysHotloadableProperties)
    }

    @Test(timeout = 300_000)
    fun `can not hotload if maxMessageSize changes`() {

        val parametersWithNewMaxMessageSize = originalNetworkParameters.copy(maxMessageSize = originalNetworkParameters.maxMessageSize + 1)
        `can not hotload`(parametersWithNewMaxMessageSize)
    }

    @Test(timeout = 300_000)
    fun `can not hotload if maxTransactionSize changes`() {

        val parametersWithNewMaxTransactionSize = originalNetworkParameters.copy(maxTransactionSize = originalNetworkParameters.maxMessageSize + 1)
        `can not hotload`(parametersWithNewMaxTransactionSize)
    }

    @Test(timeout = 300_000)
    fun `can not hotload if minimumPlatformVersion changes`() {

        val parametersWithNewMinimumPlatformVersion = originalNetworkParameters.copy(minimumPlatformVersion = originalNetworkParameters.minimumPlatformVersion + 1)
        `can not hotload`(parametersWithNewMinimumPlatformVersion)
    }

    private fun `can hotload`(newNetworkParameters: NetworkParameters) {
        val networkParametersHotloader = createHotloaderWithMockedServicesAndListener(newNetworkParameters)
        Assert.assertTrue(networkParametersHotloader.attemptHotload(newNetworkParameters.serialize().hash))
    }

    private fun `can not hotload`(newNetworkParameters: NetworkParameters) {
        val networkParametersHotloader = createHotloaderWithMockedServicesAndListener(newNetworkParameters)
        Assert.assertFalse(networkParametersHotloader.attemptHotload(newNetworkParameters.serialize().hash))
    }

    private fun createHotloaderWithMockedServicesAndListener(newNetworkParameters: NetworkParameters): NetworkParametersHotloader {
        return createHotloaderWithMockedServices(newNetworkParameters).also {
            it.addNotaryUpdateListener(object : NotaryUpdateListener {
                override fun onNewNotaryList(notaries: List<NotaryInfo>) {
                }
            })
        }
    }

    private fun createHotloaderWithMockedServices(newNetworkParameters: NetworkParameters): NetworkParametersHotloader {
        val signedNetworkParameters = networkMapCertAndKeyPair.sign(newNetworkParameters)
        val networkMapClient = Mockito.mock(NetworkMapClient::class.java)
        Mockito.`when`(networkMapClient.getNetworkParameters(newNetworkParameters.serialize().hash)).thenReturn(signedNetworkParameters)
        val networkParametersReader = Mockito.mock(NetworkParametersReader::class.java)
        Mockito.`when`(networkParametersReader.read())
                .thenReturn(NetworkParametersReader.NetworkParametersAndSigned(signedNetworkParameters, trustRoot))
        return NetworkParametersHotloader(networkMapClient, trustRoot, originalNetworkParameters, networkParametersReader, networkParametersStorage)
    }
}

