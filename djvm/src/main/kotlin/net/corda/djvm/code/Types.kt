@file:JvmName("Types")
package net.corda.djvm.code

import org.objectweb.asm.Type
import sandbox.java.lang.DJVMException
import sandbox.net.corda.djvm.costing.ThresholdViolationError
import sandbox.net.corda.djvm.rules.RuleViolationError

const val EMIT_TRACING: Int = 0
const val EMIT_TRAPPING_EXCEPTIONS: Int = 1
const val EMIT_DEFAULT: Int = 10

val ruleViolationError: String = Type.getInternalName(RuleViolationError::class.java)
val thresholdViolationError: String = Type.getInternalName(ThresholdViolationError::class.java)
val djvmException: String = Type.getInternalName(DJVMException::class.java)

/**
 * Local extension method for normalizing a class name.
 */
val String.asPackagePath: String get() = this.replace('/', '.')
val String.asResourcePath: String get() = this.replace('.', '/')

val String.emptyAsNull: String? get() = if (isEmpty()) null else this