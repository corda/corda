package net.corda.core.internal

import io.github.classgraph.ClassGraph
import net.corda.core.StubOutForDJVM

/**
 * Creates instances of all the classes in the classpath of the provided classloader, which implement the interface of the provided class.
 * @param classloader the classloader, which will be searched for the classes.
 * @param clazz the class of the interface, which the classes - to be returned - must implement.
 *
 * @return instances of the identified classes.
 * @throws IllegalArgumentException if the classes found do not have proper constructors.
 *
 * Note: In order to be instantiated, the associated classes must:
 * - be non-abstract
 * - either be a Kotlin object or have a constructor with no parameters (or only optional ones)
 */
@StubOutForDJVM
fun <T: Any> createInstancesOfClassesImplementing(classloader: ClassLoader, clazz: Class<T>): Set<T> {
    return ClassGraph().addClassLoader(classloader)
            .enableClassInfo()
            .pooledScan()
            .use {
                it.getClassesImplementing(clazz.name)
                        .filterNot { it.isAbstract }
                        .map { classloader.loadClass(it.name).asSubclass(clazz) }
                        .map { it.kotlin.objectOrNewInstance() }
                        .toSet()
            }
}

fun <T: Any?> executeWithThreadContextClassLoader(classloader: ClassLoader, fn: () -> T): T {
    val threadClassLoader = Thread.currentThread().contextClassLoader
    try {
        Thread.currentThread().contextClassLoader = classloader
        return fn()
    } finally {
        Thread.currentThread().contextClassLoader = threadClassLoader
    }

}
