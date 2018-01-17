package net.corda.node.internal.cordapp

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.internal.cordapp.CordappConfigProvider
import net.corda.core.node.services.AttachmentStorage
import net.corda.testing.node.MockCordappConfigProvider
import net.corda.testing.services.MockAttachmentStorage
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class CordappProviderImplTests {
    private companion object {
        val isolatedJAR = this::class.java.getResource("isolated.jar")!!
        // TODO: Cordapp name should differ from the JAR name
        val isolatedCordappName = "isolated"
        val emptyJAR = this::class.java.getResource("empty.jar")!!
        val validConfig = ConfigFactory.parseString("key=value")

        val stubConfigProvider = object : CordappConfigProvider {
            override fun getConfigByName(name: String): Config = ConfigFactory.empty()
        }
    }

    private lateinit var attachmentStore: AttachmentStorage

    @Before
    fun setup() {
        attachmentStore = MockAttachmentStorage()
    }

    @Test
    fun `isolated jar is loaded into the attachment store`() {
        val loader = CordappLoader.createDevMode(listOf(isolatedJAR))
        val provider = CordappProviderImpl(loader, stubConfigProvider, attachmentStore)
        val maybeAttachmentId = provider.getCordappAttachmentId(provider.cordapps.first())

        Assert.assertNotNull(maybeAttachmentId)
        Assert.assertNotNull(attachmentStore.openAttachment(maybeAttachmentId!!))
    }

    @Test
    fun `empty jar is not loaded into the attachment store`() {
        val loader = CordappLoader.createDevMode(listOf(emptyJAR))
        val provider = CordappProviderImpl(loader, stubConfigProvider, attachmentStore)
        Assert.assertNull(provider.getCordappAttachmentId(provider.cordapps.first()))
    }

    @Test
    fun `test that we find a cordapp class that is loaded into the store`() {
        val loader = CordappLoader.createDevMode(listOf(isolatedJAR))
        val provider = CordappProviderImpl(loader, stubConfigProvider, attachmentStore)
        val className = "net.corda.finance.contracts.isolated.AnotherDummyContract"

        val expected = provider.cordapps.first()
        val actual = provider.getCordappForClass(className)

        Assert.assertNotNull(actual)
        Assert.assertEquals(expected, actual)
    }

    @Test
    fun `test that we find an attachment for a cordapp contract class`() {
        val loader = CordappLoader.createDevMode(listOf(isolatedJAR))
        val provider = CordappProviderImpl(loader, stubConfigProvider, attachmentStore)
        val className = "net.corda.finance.contracts.isolated.AnotherDummyContract"
        val expected = provider.getAppContext(provider.cordapps.first()).attachmentId
        val actual = provider.getContractAttachmentID(className)

        Assert.assertNotNull(actual)
        Assert.assertEquals(actual!!, expected)
    }

    @Test
    fun `test cordapp configuration`() {
        val configProvider = MockCordappConfigProvider()
        configProvider.cordappConfigs.put(isolatedCordappName, validConfig)
        val loader = CordappLoader.createDevMode(listOf(isolatedJAR))
        val provider = CordappProviderImpl(loader, configProvider, attachmentStore)

        val expected = provider.getAppContext(provider.cordapps.first()).config

        assertThat(expected.getString("key")).isEqualTo("value")
    }
}
