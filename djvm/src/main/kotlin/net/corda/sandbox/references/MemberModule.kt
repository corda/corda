package net.corda.sandbox.references

/**
 * Member-specific functionality.
 */
@Suppress("unused")
class MemberModule : AnnotationModule() {

    /**
     * Add member definition to class.
     */
    fun addToClass(clazz: ClassRepresentation, member: Member): Member {
        clazz.members[getQualifyingIdentifier(member)] = member
        return member
    }

    /**
     * Get member definition for class. Return `null` if the member does not exist.
     */
    fun getFromClass(clazz: ClassRepresentation, memberName: String, signature: String): Member? {
        return clazz.members[getQualifyingIdentifier(memberName, signature)]
    }

    /**
     * Check if member is a constructor or a static initialization block.
     */
    fun isConstructor(member: MemberInformation): Boolean {
        return member.memberName == "<init>"        // Instance constructor
                || member.memberName == "<clinit>"  // Static initialization block
    }

    /**
     * Check if member is a field.
     */
    fun isField(member: MemberInformation): Boolean {
        return !member.signature.startsWith("(")
    }

    /**
     * Check if member is a method.
     */
    fun isMethod(member: MemberInformation): Boolean {
        return member.signature.startsWith("(")
    }

    /**
     * Check if member is marked to be deterministic.
     */
    fun isDeterministic(member: Member): Boolean {
        return isDeterministic(member.annotations)
    }

    /**
     * Check if member is marked to be non-deterministic.
     */
    fun isNonDeterministic(member: Member): Boolean {
        return isNonDeterministic(member.annotations)
    }

    /**
     * Return the number of arguments that the member expects, based on its signature.
     */
    fun numberOfArguments(signature: String): Int {
        var count = 0
        var level = 0
        var isLongName = false
        loop@ for (char in signature) {
            when {
                char == '(' -> level += 1
                char == ')' -> level -= 1
                char == '[' -> continue@loop
                !isLongName && char == 'L' -> {
                    if (level == 1) {
                        count += 1
                    }
                    isLongName = true
                }
                isLongName && char == ';' -> {
                    isLongName = false
                }
                else -> {
                    if (level == 1 && !isLongName) {
                        count += 1
                    }
                }
            }
        }
        return count
    }

    /**
     * Check whether a function returns `void` or a value/reference type.
     */
    fun returnsValueOrReference(signature: String): Boolean {
        return !signature.endsWith(")V")
    }

    /**
     * Find all classes referenced in a member's signature.
     */
    fun findReferencedClasses(member: MemberInformation): List<String> {
        val classes = mutableListOf<String>()
        var longName = StringBuilder()
        var isLongName = false
        for (char in member.signature) {
            if (char == 'L' && !isLongName) {
                longName = StringBuilder()
                isLongName = true
            } else if (char == ';' && isLongName) {
                classes.add(longName.toString())
                isLongName = false
            } else if (isLongName) {
                longName.append(char)
            }
        }
        return classes
    }

    /**
     * Get the qualifying identifier of the class member.
     */
    fun getQualifyingIdentifier(memberName: String, signature: String): String {
        return "$memberName:$signature"
    }

    /**
     * Get the qualifying identifier of the class member.
     */
    private fun getQualifyingIdentifier(member: MemberInformation): String {
        return getQualifyingIdentifier(member.memberName, member.signature)
    }

}
