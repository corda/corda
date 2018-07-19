package net.corda.gradle.jarfilter

import java.util.Collections.unmodifiableMap

/**
 * A persistent cache of all of the classes and methods that JarFilter has
 * removed. This cache belongs to the Gradle task itself and so is shared
 * by successive filter passes.
 *
 * The internal method cache is only required for those classes which are
 * being kept. When an entire class is declared as "unwanted", any entry
 * it may have in the method cache is removed.
 */
class UnwantedCache {
    private val _classes: MutableSet<String> = mutableSetOf()
    private val _classMethods: MutableMap<String, MutableSet<MethodElement>> = mutableMapOf()

    val classes: Set<String> get() = _classes
    val classMethods: Map<String, Set<MethodElement>> get() = unmodifiableMap(_classMethods)

    fun containsClass(className: String): Boolean = _classes.contains(className)

    fun addClass(className: String): Boolean {
        return _classes.add(className).also { isAdded ->
            if (isAdded) {
                _classMethods.remove(className)
            }
        }
    }

    fun addMethod(className: String, method: MethodElement) {
        if (!containsClass(className)) {
            _classMethods.getOrPut(className) { mutableSetOf() }.add(method)
        }
    }

    private fun containsMethod(className: String, method: MethodElement): Boolean {
        return _classMethods[className]?.contains(method) ?: false
    }

    fun containsMethod(className: String, methodName: String?, methodDescriptor: String?): Boolean {
        return containsClass(className) ||
                (methodName != null && methodDescriptor != null && containsMethod(className, MethodElement(methodName, methodDescriptor)))
    }
}

