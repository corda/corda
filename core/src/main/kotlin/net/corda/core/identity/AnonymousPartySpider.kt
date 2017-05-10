package net.corda.core.identity

import net.corda.core.contracts.ContractState

/**
 * Spider utility for exploring a contract state object and extracting the anonymous parties contained within.
 * Uses [ClassPartyExtractor] to extract parties from objects by default, but custom extractors can be
 * added via [registerExtractor].
 */
class AnonymousPartySpider {
    private companion object {
        val DEFAULT_EXTRACTOR = ClassPartyExtractor()
    }

    private val extractors: HashMap<Class<*>, PartyExtractor<*>> = HashMap()

    init {
        registerExtractor(AnonymousParty::class.java, AnonymousPartyExtractor())
    }

    /**
     * Register a new extractor for the given class.
     */
    fun <T: Any> registerExtractor(clazz: Class<T>, extractor: PartyExtractor<T>) {
        extractors[clazz] = extractor
    }

    /**
     * Extract the anonymous parties from a contract state.
     */
    fun extractParties(obj: ContractState) = extractParties(obj, ArrayList<Any>())

    /**
     * Extract the anonymous parties from an object.
     *
     * @param obj object to extract anonymous parties from.
     * @param written list of objects that have already been processed.
     */
    internal fun extractParties(obj: Any, written: MutableList<Any>): Set<AnonymousParty> {
        val extractor = extractors[obj.javaClass] ?: DEFAULT_EXTRACTOR
        // Find the method via reflection so that the receiving class can use generics, without problems in casting here
        val method = extractor.javaClass.getMethod("extractParties", AnonymousPartySpider::class.java, Any::class.java, MutableList::class.java) ?: throw IllegalStateException("Could not find extractParties() function")
        return method.invoke(extractor, this, obj, written) as Set<AnonymousParty>
    }
}