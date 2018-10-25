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

fun String.toDJVM(): sandbox.java.lang.String = sandbox.java.lang.String.toDJVM(this)
fun Long.toDJVM(): sandbox.java.lang.Long = sandbox.java.lang.Long.toDJVM(this)
fun Int.toDJVM(): sandbox.java.lang.Integer = sandbox.java.lang.Integer.toDJVM(this)
fun Short.toDJVM(): sandbox.java.lang.Short = sandbox.java.lang.Short.toDJVM(this)
fun Byte.toDJVM(): sandbox.java.lang.Byte = sandbox.java.lang.Byte.toDJVM(this)
fun Float.toDJVM(): sandbox.java.lang.Float = sandbox.java.lang.Float.toDJVM(this)
fun Double.toDJVM(): sandbox.java.lang.Double = sandbox.java.lang.Double.toDJVM(this)
fun Char.toDJVM(): sandbox.java.lang.Character = sandbox.java.lang.Character.toDJVM(this)
fun Boolean.toDJVM(): sandbox.java.lang.Boolean = sandbox.java.lang.Boolean.toDJVM(this)