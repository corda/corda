package net.corda.sandbox.analysis

import net.corda.sandbox.formatting.MemberFormatter
import net.corda.sandbox.references.MemberInformation

/**
 * Representation of the source location of a class, member or instruction.
 *
 * @property className The name of the class.
 * @property sourceFile The file containing the source of the compiled class.
 * @property memberName The name of the field or method.
 * @property signature The signature of the field or method.
 * @property lineNumber The index of the line from which the instruction was compiled.
 */
data class SourceLocation(
        override val className: String = "",
        val sourceFile: String = "",
        override val memberName: String = "",
        override val signature: String = "",
        val lineNumber: Int = 0
) : MemberInformation {

    /**
     * Check whether or not information about the source file is available.
     */
    val hasSourceFile: Boolean
        get() = sourceFile.isNotBlank()

    /**
     * Check whether or not information about the line number is available.
     */
    val hasLineNumber: Boolean
        get() = lineNumber != 0

    /**
     * Get a string representation of the source location.
     */
    override fun toString(): String {
        return StringBuilder().apply {
            append(className.removePrefix("sandbox/"))
            if (memberName.isNotBlank()) {
                append(".$memberName")
                if (memberFormatter.isMethod(signature)) {
                    append("(${memberFormatter.format(signature)})")
                }
            }
        }.toString()
    }

    /**
     * Get a formatted string representation of the source location.
     */
    fun format(): String {
        if (className.isBlank()) {
            return ""
        }
        return StringBuilder().apply {
            append("@|blue ")
            append(if (hasSourceFile) {
                sourceFile
            } else {
                className
            }.removePrefix("sandbox/"))
            append("|@")
            if (hasLineNumber) {
                append(" on @|yellow line $lineNumber|@")
            }
            if (memberName.isNotBlank()) {
                append(" in @|green ")
                if (hasSourceFile) {
                    append("${memberFormatter.getShortClassName(className)}.$memberName")
                } else {
                    append(memberName)
                }
                if (memberFormatter.isMethod(signature)) {
                    append("(${memberFormatter.format(signature)})")
                }
                append("|@")
            }
        }.toString()
    }

    companion object {

        private val memberFormatter = MemberFormatter()

    }

}