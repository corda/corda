package net.corda.node.services.persistence.schemas.requery

import io.requery.*
import net.corda.core.crypto.SecureHash
import net.corda.core.schemas.requery.converters.BlobConverter

@Table(name = "attachments")
@Entity(model = "persistence")
interface Attachment : Persistable {

    @get:Key
    @get:Column(name = "att_id", index = true)
    var attId: SecureHash

    @get:Column(name = "content")
    @get:Convert(BlobConverter::class)
    var content: ByteArray
}