package net.corda.core.internal

import net.corda.core.contracts.AttachmentMetadata
import java.time.Instant

class AttachmentMetadataImpl(override val attId: String, override val insertionDate: Instant, override val uploader: String?, override val filename: String?) : AttachmentMetadata