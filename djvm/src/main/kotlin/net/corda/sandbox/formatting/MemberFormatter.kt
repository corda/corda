package net.corda.sandbox.formatting

import net.corda.sandbox.references.ClassModule
import net.corda.sandbox.references.MemberInformation
import net.corda.sandbox.references.MemberModule

/**
 * Functionality for formatting a member.
 */
class MemberFormatter(
        private val classModule: ClassModule = ClassModule(),
        private val memberModule: MemberModule = MemberModule()
) {

    /**
     * Format a member.
     */
    fun format(member: MemberInformation): String {
        val className = classModule.getFormattedClassName(member.className)
        val memberName = if (memberModule.isConstructor(member)) {
            ""
        } else {
            ".${member.memberName}"
        }
        return if (memberModule.isField(member)) {
            "$className$memberName"
        } else {
            "$className$memberName(${format(member.signature)})"
        }
    }

    /**
     * Format a member's signature.
     */
    fun format(abbreviatedSignature: String): String {
        var level = 0
        val stringBuilder = StringBuilder()
        for (char in abbreviatedSignature) {
            if (char == ')') {
                level -= 1
            }
            if (level >= 1) {
                stringBuilder.append(char)
            }
            if (char == '(') {
                level += 1
            }
        }
        return generateMemberSignature(stringBuilder.toString())
    }

    /**
     * Check whether or not a signature is for a method.
     */
    fun isMethod(abbreviatedSignature: String): Boolean {
        return abbreviatedSignature.startsWith("(")
    }

    /**
     * Get the short representation of a class name.
     */
    fun getShortClassName(fullClassName: String): String {
        return classModule.getShortName(fullClassName)
    }

    /**
     * Generate a prettified version of a native signature.
     */
    private fun generateMemberSignature(abbreviatedSignature: String): String {
        return classModule.getTypes(abbreviatedSignature).joinToString(", ") {
            classModule.getShortName(it)
        }
    }

}
