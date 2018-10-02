@file:JvmName("Types")
package net.corda.djvm.code

import org.objectweb.asm.Type
import sandbox.net.corda.djvm.costing.ThresholdViolationError
import sandbox.net.corda.djvm.rules.RuleViolationError

val ruleViolationError: String = Type.getInternalName(RuleViolationError::class.java)
val thresholdViolationError: String = Type.getInternalName(ThresholdViolationError::class.java)

/**
 * Local extension method for normalizing a class name.
 */
val String.asPackagePath: String get() = this.replace('/', '.')
val String.asResourcePath: String get() = this.replace('.', '/')