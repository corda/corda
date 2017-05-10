package net.corda.core.identity

/**
 * Extractor for parties from an object.
 *
 * @param T type of object this extractor works on.
 */
interface PartyExtractor<T> {
    /**
     * Extract the parties from the given object.
     *
     * @param spider spider controlling the extraction process, for any object types this extractor doesn't know
     * how to handle.
     * @param obj object to extract from.
     * @param written list of objects that have already been processed.
     */
    fun extractParties(spider: AnonymousPartySpider, obj: T, written: MutableList<Any>): Set<AnonymousParty>
}