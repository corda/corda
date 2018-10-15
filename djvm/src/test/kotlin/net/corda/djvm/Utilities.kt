package net.corda.djvm

import sandbox.net.corda.djvm.costing.ThresholdViolationError
import sandbox.net.corda.djvm.rules.RuleViolationError

object Utilities {
    fun throwRuleViolationError(): Nothing = throw RuleViolationError("Can't catch this!")

    fun throwThresholdViolationError(): Nothing = throw ThresholdViolationError("Can't catch this!")

    fun throwContractConstraintViolation(): Nothing = throw IllegalArgumentException("Contract constraint violated")

    fun throwError(): Nothing = throw Error()

    fun throwThrowable(): Nothing = throw Throwable()

    fun throwThreadDeath(): Nothing = throw ThreadDeath()

    fun throwStackOverflowError(): Nothing = throw StackOverflowError("FAKE OVERFLOW!")

    fun throwOutOfMemoryError(): Nothing = throw OutOfMemoryError("FAKE OOM!")
}
