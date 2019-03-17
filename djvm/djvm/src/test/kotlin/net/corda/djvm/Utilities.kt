@file:JvmName("UtilityFunctions")
package net.corda.djvm

import sandbox.net.corda.djvm.costing.ThresholdViolationError
import sandbox.net.corda.djvm.rules.RuleViolationError

/**
 * Allows us to create a [Utilities] object that we can pin inside the sandbox.
 */
object Utilities {
    fun throwRuleViolationError(): Nothing = throw RuleViolationError("Can't catch this!")

    fun throwThresholdViolationError(): Nothing = throw ThresholdViolationError("Can't catch this!")
}
