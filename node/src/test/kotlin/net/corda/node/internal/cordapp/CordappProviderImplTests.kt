package net.corda.node.internal.cordapp

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.node.services.AttachmentStorage
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.MockCordappConfigProvider
import net.corda.testing.services.MockAttachmentStorage
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.URL

class CordappProviderImplTests {
    private companion object {
        val isolatedJAR = this::class.java.getResource("isolated.jar")!!
        // TODO: Cordapp name should differ from the JAR name
        const val isolatedCordappName = "isolated"
        val emptyJAR = this::class.java.getResource("empty.jar")!!
        val validConfig: Config = ConfigFactory.parseString("key=value")

        val stubConfigProvider = object : CordappConfigProvider {
            override fun getConfigByName(name: String): Config = ConfigFactory.empty()
        }
    }

    private lateinit var attachmentStore: AttachmentStorage
    private val whitelistedContractImplementations = testNetworkParameters().whitelistedContractImplementations

    @Before
    fun setup() {
        attachmentStore = MockAttachmentStorage()
    }

    @Test
    fun `isolated jar is loaded into the attachment store`() {
        val provider = newCordappProvider(isolatedJAR)
        val maybeAttachmentId = provider.getCordappAttachmentId(provider.cordapps.first())

        assertNotNull(maybeAttachmentId)
        assertNotNull(attachmentStore.openAttachment(maybeAttachmentId!!))
    }

    @Test
    fun `empty jar is not loaded into the attachment store`() {
        val provider = newCordappProvider(emptyJAR)
        assertNull(provider.getCordappAttachmentId(provider.cordapps.first()))
    }

    @Test
    fun `test that we find a cordapp class that is loaded into the store`() {
        val provider = newCordappProvider(isolatedJAR)
        val className = "net.corda.finance.contracts.isolated.AnotherDummyContract"

        val expected = provider.cordapps.first()
        val actual = provider.getCordappForClass(className)

        assertNotNull(actual)
        assertEquals(expected, actual)
    }

    @Test
    fun `test that we find an attachment for a cordapp contrat class`() {
        val provider = newCordappProvider(isolatedJAR)
        val className = "net.corda.finance.contracts.isolated.AnotherDummyContract"
        val expected = provider.getAppContext(provider.cordapps.first()).attachmentId
        val actual = provider.getContractAttachmentID(className)

        assertNotNull(actual)
        assertEquals(actual!!, expected)
    }

    @Test
    fun `test cordapp configuration`() {
        val configProvider = MockCordappConfigProvider()
        configProvider.cordappConfigs[isolatedCordappName] = validConfig
        val loader = JarScanningCordappLoader.fromJarUrls(listOf(isolatedJAR), 1000)
        val provider = CordappProviderImpl(loader, configProvider, attachmentStore).apply { start(whitelistedContractImplementations) }

        val expected = provider.getAppContext(provider.cordapps.first()).config

        assertThat(expected.getString("key")).isEqualTo("value")
    }

    private fun newCordappProvider(vararg urls: URL): CordappProviderImpl {
        val loader = JarScanningCordappLoader.fromJarUrls(urls.toList(), 1000)
        return CordappProviderImpl(loader, stubConfigProvider, attachmentStore).apply { start(whitelistedContractImplementations) }
    }
}
