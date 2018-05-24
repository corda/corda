package net.corda.sandbox.utilities

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner
import java.lang.reflect.Modifier

/**
 * Find and instantiate types that implement a certain interface.
 */
object Discovery {

    /**
     * Get an instance of each concrete class that implements interface or class [T].
     */
    inline fun <reified T> find(): List<T> {
        val instances = mutableListOf<T>()
        FastClasspathScanner("net/corda/sandbox")
                .matchClassesImplementing(T::class.java, { clazz ->
                    if (!Modifier.isAbstract(clazz.modifiers) && !Modifier.isStatic(clazz.modifiers)) {
                        try {
                            instances.add(clazz.newInstance())
                        } catch (exception: Throwable) {
                            throw Exception("Unable to instantiate ${clazz.name}", exception)
                        }
                    }
                })
                .scan()
        return instances
    }

}
