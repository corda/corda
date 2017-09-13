package net.corda.node.cordapp

import net.corda.core.node.services.AttachmentStorage
import net.corda.node.internal.cordapp.CordappLoader
import net.corda.node.internal.cordapp.CordappProvider
import net.corda.testing.node.MockAttachmentStorage
import org.junit.Assert
import org.junit.Test

class CordappProviderTests {
    @Test
    fun `isolated jar is loaded into the attachment store`() {
        val attachmentStore = MockAttachmentStorage()
        val isolatedJAR = this::class.java.getResource("isolated.jar")!!
        val loader = CordappLoader.createDevMode(listOf(isolatedJAR))
        val provider = CordappProvider(attachmentStore, loader)

        provider.start()
        val maybeAttachmentId = provider.getCordappAttachmentId(provider.cordapps.first())

        Assert.assertTrue(maybeAttachmentId.isPresent)
        Assert.assertNotNull(attachmentStore.openAttachment(maybeAttachmentId.get()))
    }
}