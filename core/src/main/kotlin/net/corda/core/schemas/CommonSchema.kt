package net.corda.core.schemas

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import org.hibernate.annotations.Type
import java.io.Serializable
import java.util.*
import javax.persistence.Column
import javax.persistence.MappedSuperclass
import javax.persistence.Transient

/**
 * JPA representation of the common schema entities
 */
object CommonSchema

/**
 * First version of the Vault ORM schema
 */
object CommonSchemaV1 : MappedSchema(schemaFamily = CommonSchema.javaClass, version = 1, mappedTypes = emptyList()) {

    @MappedSuperclass
    open class LinearState(
            /** [ContractState] attributes */

            /** X500Name of participant parties **/
            @Transient
            open var participants: MutableSet<AbstractParty>? = null,

            /**
             *  Represents a [LinearState] [UniqueIdentifier]
             */
            @Column(name = "external_id")
            var externalId: String?,

            @Column(name = "uuid", nullable = false)
            @Type(type = "uuid-char")
            var uuid: UUID

    ) : PersistentState(), Serializable {
        constructor(uid: UniqueIdentifier, _participants: Set<AbstractParty>)
                : this(participants = _participants.toMutableSet(),
                externalId = uid.externalId,
                uuid = uid.id)
    }

    @MappedSuperclass
    open class FungibleState(
            /** [ContractState] attributes */

            /** X500Name of participant parties **/
            @Transient
            open var participants: MutableSet<AbstractParty>? = null,

            /** [OwnableState] attributes */

            /** X500Name of owner party **/
            @Column(name = "owner_name")
            var owner: AbstractParty,

            /** [FungibleAsset] attributes
             *
             *  Note: the underlying Product being issued must be modelled into the
             *  custom contract itself (eg. see currency in Cash contract state)
             */

            /** Amount attributes */
            @Column(name = "quantity")
            var quantity: Long,

            /** Issuer attributes */

            /** X500Name of issuer party **/
            @Column(name = "issuer_name")
            var issuer: AbstractParty,

            @Column(name = "issuer_ref", length = MAX_ISSUER_REF_SIZE)
            @Type(type = "corda-wrapper-binary")
            var issuerRef: ByteArray
    ) : PersistentState(), Serializable
}