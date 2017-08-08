package net.corda.core.schemas

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.FungibleAsset
import net.corda.core.contracts.OwnableState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.toBase58String
import net.corda.core.identity.AbstractParty
import java.util.*
import javax.persistence.*

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
            @ElementCollection
            var participants: Set<String>,

            /**
             *  Represents a [LinearState] [UniqueIdentifier]
             */
            @Column(name = "external_id")
            var externalId: String?,

            @Column(name = "uuid", nullable = false)
            var uuid: UUID

    ) : PersistentState() {
        constructor(uid: UniqueIdentifier, _participants: Set<AbstractParty>)
            : this(participants = _participants.map { it.nameOrNull().toString() }.toSet(),
                   externalId = uid.externalId,
                   uuid = uid.id)
    }

    @MappedSuperclass
    open class FungibleState(
            /** [ContractState] attributes */

            /** X500Name of participant parties **/
            @ElementCollection
            var participants: Set<String>,

            /** [OwnableState] attributes */

            /** X500Name of anonymous owner party (after resolution by the IdentityService) **/
            var owner: String,

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
            var issuer: String,

            @Column(name = "issuer_reference")
            var issuerRef: ByteArray
    ) : PersistentState() {
        constructor(_participants: Set<AbstractParty>, _owner: AbstractParty, _quantity: Long, _issuerParty: AbstractParty, _issuerRef: ByteArray)
                : this(participants = _participants.map { it.nameOrNull().toString() }.toSet(),
                       owner = _owner.nameOrNull().toString(),
                       quantity = _quantity,
                       issuer = _issuerParty.nameOrNull().toString(),
                       issuerRef = _issuerRef)
    }
}