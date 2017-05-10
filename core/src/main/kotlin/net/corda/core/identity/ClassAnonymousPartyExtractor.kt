package net.corda.core.identity

/**
 * Default extractor for parties from an object. Uses reflection to determine fields on a class, filters out any
 * synthetic or primitive
 */
class ClassPartyExtractor : PartyExtractor<Any> {
    override fun extractParties(spider: AnonymousPartySpider, obj: Any, written: MutableList<Any>): Set<AnonymousParty> {
        return obj.javaClass.declaredFields
                // Ignore synthetic fields, which are JVM generated
                // Ignore primitive types as they're never parties nor can contain parties
                .filter { field ->
                    !field.isSynthetic
                            && !field.type.isPrimitive }
                .map { field ->
                    val previousValue = field.isAccessible
                    field.isAccessible = true
                    try {
                        field.get(obj)
                    } finally {
                        field.isAccessible = previousValue
                    }
                }
                .filter { value ->
                    value != null &&
                        !written.contains(value) }
                .flatMap { value ->
                    written.add(value)
                    spider.extractParties(value, written)
                }
                .toSet()
    }
}