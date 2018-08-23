package net.corda.djvm.references

import net.corda.djvm.utilities.loggerFor

/**
 * Representation of a hierarchy of classes.
 */
class ClassHierarchy(
        private val classModule: ClassModule = ClassModule(),
        private val memberModule: MemberModule = MemberModule()
) : Iterable<ClassRepresentation> {

    private val classMap = mutableMapOf<String, ClassRepresentation>()

    private val ancestorMap = mutableMapOf<String, List<ClassRepresentation>>()

    /**
     * Add class to the class hierarchy. If the class already exists in the class hierarchy, the existing record will
     * be overwritten by the new instance.
     *
     * @param clazz The class to add to the class hierarchy.
     */
    fun add(clazz: ClassRepresentation) {
        logger.trace("Adding type {} to hierarchy...", clazz)
        ancestorMap.clear()
        classMap[clazz.name] = clazz
    }

    /**
     * Get a class from the class hierarchy, if defined.
     */
    operator fun get(name: String) = if (classModule.isArray(name)) {
        classMap[OBJECT_NAME]
    } else {
        classMap[name]
    }

    /**
     * List of all registered class names.
     */
    val names: Set<String>
        get() = classMap.keys

    /**
     * Number of registered classes.
     */
    val size: Int
        get() = classMap.keys.size

    /**
     * Get location of class.
     */
    fun location(name: String): String = get(name)?.let {
        when {
            it.sourceFile.isNotBlank() -> it.sourceFile
            else -> it.name
        }
    } ?: ""

    /**
     * Check if a class exists in the class hierarchy.
     */
    operator fun contains(name: String) = classMap.contains(if (classModule.isArray(name)) {
        OBJECT_NAME
    } else {
        name
    })

    /**
     * Get an iterator for the defined set of classes.
     */
    override fun iterator() = classMap.values.iterator()

    /**
     * Get the implementation of a member, if defined in specified class or any of its ancestors.
     */
    fun getMember(className: String, memberName: String, signature: String): Member? {
        if (classModule.isArray(className) && memberName == ARRAY_LENGTH) {
            // Special instruction to retrieve length of array
            return Member(0, className, memberName, signature, "")
        }
        return findAncestors(get(className)).plus(get(OBJECT_NAME))
                .asSequence()
                .filterNotNull()
                .map { memberModule.getFromClass(it, memberName, signature) }
                .firstOrNull { it != null }
                .apply {
                    logger.trace("Getting rooted member for {}.{}:{} yields {}", className, memberName, signature, this)
                }
    }

    /**
     * Get all ancestors of a class.
     */
    private fun findAncestors(clazz: ClassRepresentation?): List<ClassRepresentation> {
        if (clazz == null) {
            return emptyList()
        }
        return ancestorMap.getOrPut(clazz.name) {
            val ancestors = mutableListOf(clazz)
            if (clazz.superClass.isNotEmpty()) {
                ancestors.addAll(findAncestors(get(clazz.superClass)))
            }
            ancestors.addAll(clazz.interfaces.flatMap { findAncestors(get(it)) })
            ancestors
        }
    }

    private companion object {

        private const val OBJECT_NAME = "java/lang/Object"

        private const val ARRAY_LENGTH = "length"

        private val logger = loggerFor<ClassHierarchy>()

    }

}
