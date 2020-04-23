package net.corda.core.internal

/**
 * Stubbing out non-deterministic method.
 */
fun <T: Any> createInstancesOfClassesImplementing(@Suppress("UNUSED_PARAMETER") classloader: ClassLoader, @Suppress("UNUSED_PARAMETER") clazz: Class<T>,
                                                  @Suppress("UNUSED_PARAMETER") classVersionRange: IntRange = IntRange.EMPTY): Set<T> {
    return emptySet()
}