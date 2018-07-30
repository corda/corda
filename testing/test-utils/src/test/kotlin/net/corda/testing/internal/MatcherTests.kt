package net.corda.testing.internal

import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.equalTo
import net.corda.testing.internal.matchers.hasEntries
import org.junit.Test
import kotlin.test.assertEquals

class MatcherTests {
    @Test
    fun `nested items indent`() {
        val nestedMap = mapOf(
                "a" to mapOf(
                        "apple" to "vegetable",
                        "aardvark" to "animal",
                        "anthracite" to "mineral"),
                "b" to mapOf(
                        "broccoli" to "mineral",
                        "bison" to "animal",
                        "bauxite" to "vegetable")
                )

        val matcher = hasEntries(
                "a" to hasEntries(
                        "aardvark" to equalTo("animal"),
                        "anthracite" to equalTo("mineral")
                ),
                "b" to hasEntries(
                        "bison" to equalTo("animal"),
                        "bauxite" to equalTo("mineral")
                )
        )

        assertEquals(
                """
                a map containing the entries:
                    a: a map containing the entries:
                        aardvark: is equal to "animal"
                        anthracite: is equal to "mineral"
                    b: a map containing the entries:
                        bison: is equal to "animal"
                        bauxite: is equal to "mineral"
                """.trimIndent(),
                matcher.description)

        assertEquals(
                """
                had entries which did not meet expectations:
                    b: had entries which did not meet expectations:
                        bauxite: was: "vegetable"
                """.trimIndent(),
                (matcher(nestedMap) as MatchResult.Mismatch).description
        )
    }
}