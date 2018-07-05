package net.corda.sandbox.analysis

/**
 * Functionality for resolving the class name of a sandboxed or sandboxable class.
 *
 * @param whitelist Whitelisted classes and members.
 * @property pinnedClasses Classes and packages that should be left untouched.
 * @property sandboxPrefix The package name prefix to use for classes loaded into a sandbox.
 */
class ClassResolver(
        whitelist: Whitelist,
        private val pinnedClasses: Whitelist,
        private val sandboxPrefix: String
) {

    /**
     * Classes and packages that are either pinned or already in the sandbox.
     */
    private val pinnedClassesAndSandbox =
            whitelist + pinnedClasses + Regex("^$sandboxPrefix.*$")

    /**
     * Resolve the class name from a fully qualified name.
     */
    fun resolve(name: String): String {
        return when {
            name.startsWith("[") && name.endsWith(";") -> {
                complexArrayTypeRegex.replace(name) {
                    "${it.groupValues[1]}L${resolveName(it.groupValues[2])};"
                }
            }
            name.startsWith("[") && !name.endsWith(";") -> name
            else -> resolveName(name)
        }
    }

    /**
     * Resolve the class name from a fully qualified normalized name.
     */
    fun resolveNormalized(name: String): String {
        return resolve(name.replace('.', '/')).replace('/', '.')
    }

    /**
     * Derive descriptor by resolving all referenced class names.
     */
    fun resolveDescriptor(descriptor: String): String {
        val outputDescriptor = StringBuilder()
        var longName = StringBuilder()
        var isProcessingLongName = false
        loop@ for (char in descriptor) {
            when {
                char != ';' && isProcessingLongName -> {
                    longName.append(char)
                    continue@loop
                }
                char == 'L' -> {
                    isProcessingLongName = true
                    longName = StringBuilder()
                }
                char == ';' -> {
                    outputDescriptor.append(resolve(longName.toString()))
                    isProcessingLongName = false
                }
            }
            outputDescriptor.append(char)
        }
        return outputDescriptor.toString()
    }

    /**
     * Reverse the resolution of a class name.
     */
    fun reverse(resolvedClassName: String): String {
        if (pinnedClasses.matches(resolvedClassName)) {
            return resolvedClassName
        }
        if (resolvedClassName.startsWith(sandboxPrefix)) {
            val nameWithoutPrefix = resolvedClassName.drop(sandboxPrefix.length)
            if (resolve(nameWithoutPrefix) == resolvedClassName) {
                return nameWithoutPrefix
            }
        }
        return resolvedClassName
    }

    /**
     * Reverse the resolution of a class name from a fully qualified normalized name.
     */
    fun reverseNormalized(name: String): String {
        return reverse(name.replace('.', '/')).replace('/', '.')
    }

    /**
     * Resolve class name from a fully qualified name.
     */
    private fun resolveName(name: String): String {
        return if (name.isBlank() || isPinnedClass(name)) {
            name
        } else {
            "$sandboxPrefix$name"
        }
    }

    /**
     * Check if class is pinned.
     */
    private fun isPinnedClass(name: String): Boolean =
            pinnedClassesAndSandbox.matches(name)

    companion object {
        private val complexArrayTypeRegex = "^(\\[+)L(.*);$".toRegex()
    }

}