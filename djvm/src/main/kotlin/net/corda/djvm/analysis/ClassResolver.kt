package net.corda.djvm.analysis

/**
 * Functionality for resolving the class name of a sandboxable class.
 *
 * The resolution of a class name entails determining whether the class can be instrumented or not. This means that the
 * following criteria need to be satisfied:
 *  - The class do not reside in the "java/lang" package.
 *  - The class has not been explicitly pinned.
 *  - The class does not already reside in the top-level package named [sandboxPrefix].
 *
 * If these criteria have been satisfied, the fully-qualified class name will be derived by prepending [sandboxPrefix]
 * to it. Note that [ClassLoader] will not allow defining a class in a package whose fully-qualified class name starts
 * with "java/". That will result in the class loader throwing [SecurityException]. Also, some values map onto types
 * defined in "java/lang/", e.g., [Integer] and [String]. These cannot be trivially moved into a different package due
 * to the internal mechanisms of the JVM.
 *
 * @property pinnedClasses Classes that have already been declared in the sandbox namespace and that should be made
 * available inside the sandboxed environment.
 * @property whitelist The set of classes in the Java runtime libraries that have been whitelisted and that should be
 * left alone.
 * @property sandboxPrefix The package name prefix to use for classes loaded into a sandbox.
 */
class ClassResolver(
        private val pinnedClasses: Set<String>,
        private val whitelist: Whitelist,
        private val sandboxPrefix: String
) {

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
        if (resolvedClassName in pinnedClasses) {
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
        return if (isPinnedOrWhitelistedClass(name)) {
            name
        } else {
            "$sandboxPrefix$name"
        }
    }

    /**
     * Check if class is whitelisted or pinned.
     */
    private fun isPinnedOrWhitelistedClass(name: String): Boolean {
        return whitelist.matches(name) ||
                name in pinnedClasses ||
                sandboxRegex.matches(name)
    }

    private val sandboxRegex = "^$sandboxPrefix.*$".toRegex()

    companion object {
        private val complexArrayTypeRegex = "^(\\[+)L(.*);$".toRegex()
    }

}