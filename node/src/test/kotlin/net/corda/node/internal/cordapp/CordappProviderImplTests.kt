package net.corda.node.internal.cordapp

import net.corda.core.node.services.AttachmentStorage
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.services.MockAttachmentStorage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.URL

class CordappProviderImplTests {
    companion object {
        private val isolatedJAR = this::class.java.getResource("isolated.jar")!!
        private val emptyJAR = this::class.java.getResource("empty.jar")!!
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

    private fun newCordappProvider(vararg urls: URL): CordappProviderImpl {
        val loader = CordappLoader.createDevMode(urls.toList())
        return CordappProviderImpl(loader, attachmentStore, whitelistedContractImplementations)
    }
}
