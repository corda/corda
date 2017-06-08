package net.corda.core.identity

/**
 * Extractor for when an [AnonymousParty] is encountered, returns the object directly.
 */
class AnonymousPartyExtractor : PartyExtractor<AnonymousParty> {
    override fun extractParties(spider: AnonymousPartySpider, obj: AnonymousParty, written: MutableList<Any>): Set<AnonymousParty> {
        return setOf(obj)
    }
}