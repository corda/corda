package net.corda.services.schemas

import net.corda.core.schemas.MappedSchema
import java.io.Serializable
import javax.persistence.*

/**
 * JPA representation of the attachment service schema entity
 */
object AttachmentsSchema

/**
 * First version of the Vault ORM schema
 */
object AttachmentsSchemaV1 : MappedSchema(schemaFamily = AttachmentsSchema.javaClass, version = 1,
                                          mappedTypes = listOf(Attachment::class.java)) {
    @Entity
    @Table(name = "attachments",
           indexes = arrayOf(Index(name = "att_id_idx", columnList = "att_id")))
    class Attachment(
        @Id
        @Column(name = "att_id", length = 65535)
        var attId: String,

        @Column(name = "content")
        @Lob
        var content: ByteArray
    ) : Serializable

}