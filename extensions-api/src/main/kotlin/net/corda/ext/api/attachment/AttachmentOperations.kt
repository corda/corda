package net.corda.ext.api.attachment

import net.corda.core.CordaInternal
import net.corda.core.internal.AttachmentTrustCalculator

@CordaInternal
interface AttachmentOperations : AttachmentTrustCalculator, AttachmentImporter {
}