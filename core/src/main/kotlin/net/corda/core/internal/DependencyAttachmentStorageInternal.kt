package net.corda.core.internal

import net.corda.core.CordaInternal
import net.corda.core.contracts.ContractAttachment
import net.corda.core.node.services.AttachmentStorage

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
    @CordaInternal
    fun internalFindTrustedAttachmentForClass(className: String): ContractAttachment?
}