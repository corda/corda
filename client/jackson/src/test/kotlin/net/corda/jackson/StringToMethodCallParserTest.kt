package net.corda.jackson

import net.corda.core.crypto.SecureHash
import org.junit.Test
import kotlin.test.assertEquals

class StringToMethodCallParserTest {
    @Suppress("UNUSED")
    class Target {
        fun simple() = "simple"
        fun string(note: String) = note
        fun twoStrings(a: String, b: String) = a + b
        fun simpleObject(hash: SecureHash.SHA256) = hash.toString()!!
        fun complexObject(pair: Pair<Int, String>) = pair
    }

    val randomHash = "361170110f61086f77ff2c5b7ab36513705da1a3ebabf14dbe5cc9c982c45401"
    val tests = mapOf(
            "simple" to "simple",
            "string note: A test of barewords" to "A test of barewords",
            "twoStrings a: Some words, b: ' and some words, like, Kirk, would, speak'" to "Some words and some words, like, Kirk, would, speak",
            "simpleObject hash: $randomHash" to randomHash.toUpperCase(),
            "complexObject pair: { first: 12, second: Word up brother }" to Pair(12, "Word up brother")
    )

    @Test
    fun calls() {
        val parser = StringToMethodCallParser(Target::class)
        val target = Target()
        for ((input, output) in tests) {
            assertEquals(output, parser.parse(target, input).invoke())
        }
    }
}