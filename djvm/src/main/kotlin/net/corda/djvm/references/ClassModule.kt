package net.corda.djvm.references

/**
 * Class-specific functionality.
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
class ClassModule : AnnotationModule() {

    /**
     * Check if class representation is an array.
     */
    fun isArray(className: String): Boolean {
        return className.startsWith('[')
    }

    /**
     * Check if class is marked to be deterministic.
     */
    fun isDeterministic(clazz: ClassRepresentation): Boolean {
        return isDeterministic(clazz.annotations)
    }

    /**
     * Check if class is marked to be non-deterministic.
     */
    fun isNonDeterministic(clazz: ClassRepresentation): Boolean {
        return isNonDeterministic(clazz.annotations)
    }

    /**
     * Get the full source location for a class based on the package name and the source file.
     */
    fun getFullSourceLocation(clazz: ClassRepresentation, source: String? = null): String {
        val sourceFile = source ?: clazz.sourceFile
        return if ('/' in clazz.name) {
            "${clazz.name.substring(0, clazz.name.lastIndexOf('/'))}/$sourceFile"
        } else {
            sourceFile
        }
    }

    /**
     * Get the binary version of a class name.
     */
    fun getBinaryClassName(name: String) =
            normalizeClassName(name).replace('.', '/')

    /**
     * Get the formatted version of a class name.
     */
    fun getFormattedClassName(name: String) =
            normalizeClassName(name).replace('/', '.')

    /**
     * Get the short name of a class.
     */
    fun getShortName(name: String): String {
        val className = getFormattedClassName(name)
        return if ('.' in className) {
            className.removeRange(0, className.lastIndexOf('.') + 1)
        } else {
            className
        }
    }

    /**
     * Normalize an abbreviated class name.
     */
    fun normalizeClassName(name: Char): String = normalizeClassName("$name")

    /**
     * Normalize an abbreviated class name.
     */
    fun normalizeClassName(name: String): String = when {
        name == "V" -> "java/lang/Void"
        name == "Z" -> "java/lang/Boolean"
        name == "B" -> "java/lang/Byte"
        name == "C" -> "java/lang/Character"
        name == "S" -> "java/lang/Short"
        name == "I" -> "java/lang/Integer"
        name == "J" -> "java/lang/Long"
        name == "F" -> "java/lang/Float"
        name == "D" -> "java/lang/Double"
        name.startsWith("L") && name.endsWith(";") ->
            name.substring(1, name.length - 1)
        name.startsWith("[") ->
            normalizeClassName(name.substring(1)) + "[]"
        else -> name
    }

    /**
     * Get a list of types referenced in a signature.
     */
    fun getTypes(abbreviatedSignature: String): List<String> {
        val types = mutableListOf<String>()
        var isArray = false
        var arrayLevel = 0
        var isLongName = false
        var longName = StringBuilder()
        for (char in abbreviatedSignature) {
            if (char in arrayOf('(', ')')) {
                continue
            } else if (char == '[') {
                isArray = true
                arrayLevel += 1
            } else if (char == 'L' && !isLongName) {
                isLongName = true
                longName = StringBuilder()
            } else if (char == ';' && isLongName) {
                val type = longName.toString()
                if (isArray) {
                    types.add(type + "[]".repeat(arrayLevel))
                } else {
                    types.add(type)
                }
                isLongName = false
                isArray = false
                arrayLevel = 0
            } else if (!isLongName) {
                val type = normalizeClassName(char)
                if (type.isNotBlank()) {
                    if (isArray) {
                        types.add(type + "[]".repeat(arrayLevel))
                    } else {
                        types.add(type)
                    }
                    isLongName = false
                    isArray = false
                    arrayLevel = 0
                }
            } else {
                longName.append(char)
            }
        }
        return types
    }

    /**
     * Get all classes referenced from set of annotation descriptors.
     */
    fun getClassReferencesFromAnnotations(annotations: Set<String>, derive: Boolean): List<String> {
        return when {
            !derive -> emptyList()
            else -> annotations
                    .flatMap { getClassReferencesFromSignature(it) }
                    .filterOutPrimitiveTypes()
        }
    }

    /**
     * Get all classes referenced from a class definition.
     */
    fun getClassReferencesFromClass(clazz: ClassRepresentation, derive: Boolean): List<String> {
        val classes = (clazz.interfaces + clazz.superClass).filter(String::isNotBlank) +
                getClassReferencesFromAnnotations(clazz.annotations, derive) +
                getClassReferencesFromGenericsSignature(clazz.genericsDetails) +
                getClassReferencesFromMembers(clazz.members.values, derive)
        return classes.filterOutPrimitiveTypes()
    }

    /**
     * Get all classes referenced from a set of member definitions.
     */
    fun getClassReferencesFromMembers(members: Iterable<Member>, derive: Boolean): List<String> {
        return members
                .flatMap { getClassReferencesFromMember(it, derive) }
                .filterOutPrimitiveTypes()
    }

    /**
     * Get all classes referenced from a member definition.
     */
    fun getClassReferencesFromMember(member: Member, derive: Boolean): List<String> {
        val classes = getClassReferencesFromSignature(member.signature) +
                getClassReferencesFromAnnotations(member.annotations, derive) +
                getClassReferencesFromGenericsSignature(member.genericsDetails)
        return classes.filterOutPrimitiveTypes()
    }

    /**
     * Get all classes referenced from a member signature.
     */
    fun getClassReferencesFromSignature(signature: String): List<String> {
        return getTypes(signature)
                .filterOutPrimitiveTypes()
    }

    /**
     * Get all classes referenced from a generics signature.
     */
    fun getClassReferencesFromGenericsSignature(signature: String): List<String> {
        return getTypes(signature.replace(genericTypeSignatureRegex, ";"))
                .filter { it.contains("/") }
                .filterOutPrimitiveTypes()
    }

    /**
     * Filter out primitive types and clean up array types.
     */
    private fun List<String>.filterOutPrimitiveTypes(): List<String> {
        return this.map { it.replace("[", "").replace("]", "") }
                .filter { it.length > 1 }
                .distinct()
    }

    private val genericTypeSignatureRegex = "[<>:]".toRegex()

}
