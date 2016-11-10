package net.corda.explorer.views

import javafx.application.Platform
import javafx.util.StringConverter

/**
 *  Helper method to reduce boiler plate code
 */
fun <T> stringConverter(fromStringFunction: ((String?) -> T)? = null, toStringFunction: (T) -> String): StringConverter<T> {
    val converter = object : StringConverter<T>() {
        override fun fromString(string: String?): T {
            return fromStringFunction?.invoke(string) ?: throw UnsupportedOperationException("not implemented")
        }

        override fun toString(o: T): String {
            return toStringFunction(o)
        }
    }
    return converter
}

/**
 * Format Number to string with metric prefix.
 */
fun Number.toStringWithSuffix(precision: Int = 1): String {
    if (this.toDouble() < 1000) return "$this"
    val exp = (Math.log(this.toDouble()) / Math.log(1000.0)).toInt()
    return "${(this.toDouble() / Math.pow(1000.0, exp.toDouble())).format(precision)} ${"kMGTPE"[exp - 1]}"
}

fun Double.format(precision: Int) = String.format("%.${precision}f", this)

/**
 * Helper method to make sure block runs in FX thread
 */
fun runInFxApplicationThread(block: () -> Unit) {
    if (Platform.isFxApplicationThread()) {
        block()
    } else {
        Platform.runLater(block)
    }
}
