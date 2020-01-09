package net.corda.node.internal.attachment

import net.corda.core.internal.AttachmentTrustCalculator
import net.corda.ext.api.attachment.AttachmentImporter
import net.corda.ext.api.attachment.AttachmentOperations

class AttachmentOperationsImpl(importer: AttachmentImporter, attachmentTrustCalculator: AttachmentTrustCalculator) : AttachmentOperations,
        AttachmentTrustCalculator by attachmentTrustCalculator, AttachmentImporter by importer