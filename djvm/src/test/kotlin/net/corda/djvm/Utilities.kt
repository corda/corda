package net.corda.djvm

import sandbox.net.corda.djvm.costing.ThresholdViolationError
import sandbox.net.corda.djvm.rules.RuleViolationError

object Utilities {
    fun throwRuleViolationError(): Nothing = throw RuleViolationError("Can't catch this!")

    fun throwThresholdViolationError(): Nothing = throw ThresholdViolationError("Can't catch this!")
}
