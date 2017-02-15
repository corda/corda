package net.corda.node.services.persistence

import io.requery.*
import net.corda.core.crypto.SecureHash
import net.corda.node.utilities.NODE_DATABASE_PREFIX

@Table(name = "${NODE_DATABASE_PREFIX}attachments")
@Entity interface IAttachmentTable : Persistable {
    @get:Key
    @get:Column(name = "ATT_ID", index = true)
    var attId: SecureHash

    @get:Column(name = "CONTENT")
    @get:Convert(BlobConverter::class)
    var content: ByteArray
}