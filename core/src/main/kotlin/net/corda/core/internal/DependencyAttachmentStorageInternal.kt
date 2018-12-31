package net.corda.core.internal

import net.corda.core.contracts.ContractAttachment
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.node.services.vault.AttachmentQueryCriteria
import net.corda.core.node.services.vault.AttachmentSort
import net.corda.core.node.services.vault.Builder
import net.corda.core.node.services.vault.Sort
import java.util.jar.JarInputStream

interface DependencyAttachmentStorageInternal : AttachmentStorage {

    /**
     * Scans trusted (installed locally) contract attachments to find all that contain the [className].
     * This is required as a workaround until explicit cordapp dependencies are implemented.
     * DO NOT USE IN CLIENT code.
     *
     * @return the contract attachments with the highest version.
     *
     * TODO: Should throw when the class is found in multiple contract attachments (not different versions).
     */
    fun internalFindTrustedAttachmentForClass(className: String): ContractAttachment?{
        val allTrusted = queryAttachments(
                AttachmentQueryCriteria.AttachmentsQueryCriteria().withUploader(Builder.`in`(TRUSTED_UPLOADERS)),
                AttachmentSort(listOf(AttachmentSort.AttachmentSortColumn(AttachmentSort.AttachmentSortAttribute.VERSION, Sort.Direction.DESC))))

        // TODO - add caching if performance is affected.
        for (attId in allTrusted) {
            val attch = openAttachment(attId)!!
            if (attch is ContractAttachment && attch.openAsJAR().use { hasFile(it, "$className.class") }) return attch
        }
        return null
    }

    private fun hasFile(jarStream: JarInputStream, className: String): Boolean {
        while (true) {
            val e = jarStream.nextJarEntry ?: return false
            if (e.name == className) {
                return true
            }
        }
    }

}