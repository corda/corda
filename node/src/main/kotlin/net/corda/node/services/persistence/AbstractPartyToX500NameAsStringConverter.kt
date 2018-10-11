package net.corda.node.services.persistence

import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.utilities.contextLogger
import javax.persistence.AttributeConverter
import javax.persistence.Converter

/**
 * Converter to persist a party as its' well known identity (where resolvable).
 * Completely anonymous parties are stored as null (to preserve privacy).
 */
@Converter(autoApply = true)
class AbstractPartyToX500NameAsStringConverter(private var wellKnownPartyFromX500Name: ((CordaX500Name) -> Party?)?,
                                               private var wellKnownPartyFromAnonymous: ((AbstractParty) -> Party?)?) : AttributeConverter<AbstractParty, String>, AutoCloseable {
    companion object {
        private val log = contextLogger()
    }

    init {
        require(wellKnownPartyFromX500Name != null)
        require(wellKnownPartyFromAnonymous != null)
    }

    override fun convertToDatabaseColumn(party: AbstractParty?): String? {
        if (party != null) {
            val partyName = wellKnownPartyFromAnonymous!!(party)?.toString()
            if (partyName != null) return partyName
            log.warn("Identity service unable to resolve AbstractParty: $party")
        }
        return null // non resolvable anonymous parties
    }

    override fun convertToEntityAttribute(dbData: String?): AbstractParty? {
        if (dbData != null) {
            val party = wellKnownPartyFromX500Name!!(CordaX500Name.parse(dbData))
            if (party != null) return party
            log.warn("Identity service unable to resolve X500name: $dbData")
        }
        return null // non resolvable anonymous parties are stored as nulls
    }

    override fun close() {
        // Forget about callbacks that been previously provided.
        log.info("Closing AbstractPartyToX500NameAsStringConverter")
        wellKnownPartyFromX500Name = null
        wellKnownPartyFromAnonymous = null
    }
}