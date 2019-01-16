package net.corda.core.internal

/**
 * Stubbing out non-deterministic method.
 */
fun <T: Any> createInstancesOfClassesImplementing(classloader: ClassLoader, clazz: Class<T>): Set<T> {
    return emptySet()
}