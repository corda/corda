package net.corda.djvm.utilities

import io.github.classgraph.ClassGraph
import java.lang.reflect.Modifier

/**
 * Find and instantiate types that implement a certain interface.
 */
object Discovery {
    const val FORBIDDEN_CLASS_MASK = (Modifier.STATIC or Modifier.ABSTRACT or Modifier.PRIVATE or Modifier.PROTECTED)

    /**
     * Get an instance of each concrete class that implements interface or class [T].
     */
    inline fun <reified T> find(): List<T> {
        return ClassGraph()
                .whitelistPaths("net/corda/djvm")
                .enableAllInfo()
                .scan()
                .use { it.getClassesImplementing(T::class.java.name).loadClasses(T::class.java) }
                .filter { it.modifiers and FORBIDDEN_CLASS_MASK == 0 }
                .map {
                    try {
                        it.newInstance()
                    } catch (exception: Throwable) {
                        throw Exception("Unable to instantiate ${it.name}", exception)
                    }
                }

    }
}
