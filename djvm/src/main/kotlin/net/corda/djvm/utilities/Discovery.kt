package net.corda.djvm.utilities

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner
import java.lang.reflect.Modifier

/**
 * Find and instantiate types that implement a certain interface.
 */
object Discovery {
    const val FORBIDDEN_CLASS_MASK = (Modifier.STATIC or Modifier.ABSTRACT)

    /**
     * Get an instance of each concrete class that implements interface or class [T].
     */
    inline fun <reified T> find(): List<T> {
        val instances = mutableListOf<T>()
        FastClasspathScanner("net/corda/djvm")
                .matchClassesImplementing(T::class.java) { clazz ->
                    if (clazz.modifiers and FORBIDDEN_CLASS_MASK == 0) {
                        try {
                            instances.add(clazz.newInstance())
                        } catch (exception: Throwable) {
                            throw Exception("Unable to instantiate ${clazz.name}", exception)
                        }
                    }
                }
                .scan()
        return instances
    }

}
