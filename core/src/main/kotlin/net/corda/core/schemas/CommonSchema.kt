package net.corda.node.services.vault.schemas.jpa

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.toBase58String
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.StatePersistable
import java.util.*
import javax.persistence.*

/**
 * JPA representation of the common schema entities
 */
object CommonSchema

/**
 * First version of the Vault ORM schema
 */
object CommonSchemaV1 : MappedSchema(schemaFamily = CommonSchema.javaClass, version = 1, mappedTypes = listOf(Party::class.java)) {

    @MappedSuperclass
    open class LinearState(
            /**
             *  Represents a [LinearState] [UniqueIdentifier]
             */
            @Column(name = "external_id")
            var externalId: String?,

            @Column(name = "uuid", nullable = false)
            var uuid: UUID

    ) : PersistentState() {
        constructor(uid: UniqueIdentifier) : this(externalId = uid.externalId, uuid = uid.id)
    }

    @MappedSuperclass
    open class FungibleState(
            /** [ContractState] attributes */
            @OneToMany(cascade = arrayOf(CascadeType.ALL))
            var participants: Set<CommonSchemaV1.Party>,

            /** [OwnableState] attributes */
            @OneToOne(cascade = arrayOf(CascadeType.ALL))
            var ownerKey: CommonSchemaV1.Party,

            /** [FungibleAsset] attributes
             *
             *  Note: the underlying Product being issued must be modelled into the
             *  custom contract itself (eg. see currency in Cash contract state)
             */

            /** Amount attributes */
            @Column(name = "quantity")
            var quantity: Long,

            /** Issuer attributes */
            @OneToOne(cascade = arrayOf(CascadeType.ALL))
            var issuerParty: CommonSchemaV1.Party,

            @Column(name = "issuer_reference")
            var issuerRef: ByteArray
    ) : PersistentState() {
        constructor(_participants: Set<AbstractParty>, _ownerKey: AbstractParty, _quantity: Long, _issuerParty: AbstractParty, _issuerRef: ByteArray)
                : this(participants = _participants.map { CommonSchemaV1.Party(it) }.toSet(),
                       ownerKey = CommonSchemaV1.Party(_ownerKey),
                       quantity = _quantity,
                       issuerParty = CommonSchemaV1.Party(_issuerParty),
                       issuerRef = _issuerRef)
    }

    /**
     *  Party entity (to be replaced by referencing final Identity Schema)
     */
    @Entity
    @Table(name = "vault_party",
            indexes = arrayOf(Index(name = "party_name_idx", columnList = "party_name")))
    class Party(
            @Id
            @GeneratedValue
            @Column(name = "party_id")
            var id: Int,

            /**
             * [Party] attributes
             */
            @Column(name = "party_name")
            var name: String,

            @Column(name = "party_key", length = 65535) // TODO What is the upper limit on size of CompositeKey?)
            var key: String
    ) {
        constructor(party: net.corda.core.identity.AbstractParty)
                : this(0, party.nameOrNull()?.toString() ?: party.toString(), party.owningKey.toBase58String())
    }
}