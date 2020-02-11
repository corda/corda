package net.corda.core.internal

import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import net.corda.core.StubOutForDJVM
import net.corda.core.serialization.internal.AttachmentURLStreamHandlerFactory.attachmentScheme

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
    return getNamesOfClassesImplementing(classloader, clazz)
        .map { classloader.loadClass(it).asSubclass(clazz) }
        .mapTo(LinkedHashSet()) { it.kotlin.objectOrNewInstance() }
}

/**
 * Scans for all the non-abstract classes in the classpath of the provided classloader which implement the interface of the provided class.
 * @param classloader the classloader, which will be searched for the classes.
 * @param clazz the class of the interface, which the classes - to be returned - must implement.
 *
 * @return names of the identified classes.
 */
@StubOutForDJVM
fun <T: Any> getNamesOfClassesImplementing(classloader: ClassLoader, clazz: Class<T>): Set<String> {
    return ClassGraph().overrideClassLoaders(classloader)
        .enableURLScheme(attachmentScheme)
        .ignoreParentClassLoaders()
        .enableClassInfo()
        .pooledScan()
        .use { result ->
            result.getClassesImplementing(clazz.name)
                .filterNot(ClassInfo::isAbstract)
                .mapTo(LinkedHashSet(), ClassInfo::getName)
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
