package net.corda.core.internal

import io.github.classgraph.ClassGraph
import net.corda.core.StubOutForDJVM
import kotlin.reflect.full.createInstance

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
fun <T: Any> loadClassesImplementing(classloader: ClassLoader, clazz: Class<T>): Set<T> {
    return ClassGraph().addClassLoader(classloader)
            .enableClassInfo()
            .scan()
            .getClassesImplementing(clazz.name)
            .filterNot { it.isAbstract }
            .mapNotNull { classloader.loadClass(it.name).asSubclass(clazz) }
            .map { it.kotlin.objectInstance ?: it.kotlin.createInstance() }
            .toSet()
}