package net.corda.services.schemas

import net.corda.core.crypto.SecureHash
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.requery.converters.BlobConverter
import net.corda.core.schemas.requery.converters.SecureHashConverter
import java.io.Serializable
import javax.persistence.*

/**
 * JPA representation of the attachment service schema entity
 */
object AttachmentsSchema

/**
 * First version of the Vault ORM schema
 */
object AttachmentsSchemaV1 : MappedSchema(schemaFamily = AttachmentsSchema.javaClass, version = 1, mappedTypes = emptyList()) {

    @Table(name = "attachments",
           indexes = arrayOf(Index(name = "att_id_idx", columnList = "att_id")))
    class Attachment(
        @Id
        @GeneratedValue
        @Column(name = "att_id")
        @Convert(converter = SecureHashConverter::class)
        var attId: SecureHash,

        @Column(name = "content")
        @Convert(converter = BlobConverter::class)
        var content: ByteArray
    ) : Serializable

}