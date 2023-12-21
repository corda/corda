package net.corda.core.internal

import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import net.corda.core.serialization.internal.AttachmentURLStreamHandlerFactory.ATTACHMENT_SCHEME

/**
 * Creates instances of all the classes in the classpath of the provided classloader, which implement the interface of the provided class.
 * @param classloader the classloader, which will be searched for the classes.
 * @param clazz the class of the interface, which the classes - to be returned - must implement.
 * @param classVersionRange if specified an exception is raised if class version is not within the passed range.
 *
 * @return instances of the identified classes.
 * @throws IllegalArgumentException if the classes found do not have proper constructors.
 * @throws UnsupportedClassVersionError if the class version is not within range.
 *
 * Note: In order to be instantiated, the associated classes must:
 * - be non-abstract
 * - either be a Kotlin object or have a constructor with no parameters (or only optional ones)
 */
fun <T: Any> createInstancesOfClassesImplementing(classloader: ClassLoader, clazz: Class<T>,
                                                  classVersionRange: IntRange? = null): Set<T> {
    return getNamesOfClassesImplementing(classloader, clazz, classVersionRange)
        .mapToSet { loadClassOfType(clazz, it, false, classloader).kotlin.objectOrNewInstance() }
}

/**
 * Scans for all the non-abstract classes in the classpath of the provided classloader which implement the interface of the provided class.
 * @param classloader the classloader, which will be searched for the classes.
 * @param clazz the class of the interface, which the classes - to be returned - must implement.
 * @param classVersionRange if specified an exception is raised if class version is not within the passed range.
 *
 * @return names of the identified classes.
 * @throws UnsupportedClassVersionError if the class version is not within range.
 */
fun <T: Any> getNamesOfClassesImplementing(classloader: ClassLoader, clazz: Class<T>,
                                           classVersionRange: IntRange? = null): Set<String> {
    val isJava11 = JavaVersion.isVersionAtLeast(JavaVersion.Java_11)

    return ClassGraph().apply {
            if (!isJava11 || classloader !== ClassLoader.getSystemClassLoader()) {
                overrideClassLoaders(classloader)
            }
        }
        .enableURLScheme(ATTACHMENT_SCHEME)
        .ignoreParentClassLoaders()
        .enableClassInfo()
        .pooledScan()
        .use { result ->
            classVersionRange?.let {
                result.allClasses.firstOrNull { c -> c.classfileMajorVersion !in classVersionRange }?.also {
                    throw UnsupportedClassVersionError("Class ${it.name} found in ${it.classpathElementURL} " +
                            "has an unsupported class version of ${it.classfileMajorVersion}")
                }
            }
            result.getClassesImplementing(clazz.name)
                .filterNot(ClassInfo::isAbstract)
                .mapToSet(ClassInfo::getName)
        }
}

/**
 * @throws ClassNotFoundException
 * @throws ClassCastException
 * @see Class.forName
 */
inline fun <reified T> loadClassOfType(className: String, initialize: Boolean = true, classLoader: ClassLoader? = null): Class<out T> {
    return loadClassOfType(T::class.java, className, initialize, classLoader)
}

fun <T> loadClassOfType(type: Class<T>, className: String, initialize: Boolean = true, classLoader: ClassLoader? = null): Class<out T> {
    return Class.forName(className, initialize, classLoader).asSubclass(type)
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
