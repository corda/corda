package net.corda.client.jackson

import net.corda.core.crypto.SecureHash
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import kotlin.test.assertEquals

class StringToMethodCallParserTest {
    @Suppress("UNUSED")
    class Target {
        fun simple() = "simple"
        fun string(noteTextWord: String) = noteTextWord
        fun twoStrings(a: String, b: String) = a + b
        fun simpleObject(hash: SecureHash.SHA256) = hash.toString()
        fun complexObject(pair: Pair<Int, String>) = pair

        fun overload(a: String) = a
        fun overload(a: String, b: String) = a + b
    }

    val randomHash = "361170110f61086f77ff2c5b7ab36513705da1a3ebabf14dbe5cc9c982c45401"
    val tests = mapOf(
            "simple" to "simple",
            "string noteTextWord: A test of barewords" to "A test of barewords",
            "twoStrings a: Some words, b: ' and some words, like, Kirk, would, speak'" to "Some words and some words, like, Kirk, would, speak",
            "simpleObject hash: $randomHash" to randomHash.toUpperCase(),
            "complexObject pair: { first: 12, second: Word up brother }" to Pair(12, "Word up brother"),
            "overload a: A" to "A",
            "overload a: A, b: B" to "AB"
    )

    @Test
    fun calls() {
        val parser = StringToMethodCallParser(Target::class)
        val target = Target()
        for ((input, output) in tests) {
            assertEquals(output, parser.parse(target, input).invoke())
        }
    }

    @Suppress("UNUSED")
    class ConstructorTarget(val someWord: String, val aDifferentThing: Int) {
        constructor(alternativeWord: String) : this(alternativeWord, 0)
    }

    @Test
    fun ctor1() {
        val clazz = ConstructorTarget::class.java
        val parser = StringToMethodCallParser(clazz)
        val ctor = clazz.constructors.single { it.parameterCount == 2 }
        val names: List<String> = parser.paramNamesFromConstructor(ctor)
        assertEquals(listOf("someWord", "aDifferentThing"), names)
        val args: Array<Any?> = parser.parseArguments(clazz.name, names.zip(ctor.parameterTypes), "someWord: Blah blah blah, aDifferentThing: 12")
        assertArrayEquals(args, arrayOf<Any?>("Blah blah blah", 12))
    }

    @Test
    fun ctor2() {
        val clazz = ConstructorTarget::class.java
        val parser = StringToMethodCallParser(clazz)
        val ctor = clazz.constructors.single { it.parameterCount == 1 }
        val names: List<String> = parser.paramNamesFromConstructor(ctor)
        assertEquals(listOf("alternativeWord"), names)
        val args: Array<Any?> = parser.parseArguments(clazz.name, names.zip(ctor.parameterTypes), "alternativeWord: Foo bar!")
        assertArrayEquals(args, arrayOf<Any?>("Foo bar!"))
    }
}
