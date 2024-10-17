package net.corda.testing.node.internal

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.matchesPattern
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.stream.Collectors

@RunWith(value = Parameterized::class)
class CordaCliWrapperErrorHandlingTests(val arguments: List<String>, val outputRegexPattern: String) {

    companion object {
        val className = "net.corda.testing.node.internal.SampleCordaCliWrapper"

        private val stackTraceRegex = "^.+Exception[^\\n]++(\\s+at .++)+[\\s\\S]*"
        private val exceptionWithoutStackTraceRegex ="(\\?\\[31m)*\\Q${className}\\E(\\?\\[0m)*(\\s+.+)"
        private val emptyStringRegex = "^$"

        @JvmStatic
        @Parameterized.Parameters
        fun data() = listOf(
                arrayOf(listOf("--throw-exception", "--verbose"), stackTraceRegex),
                arrayOf(listOf("--throw-exception"), exceptionWithoutStackTraceRegex),
                arrayOf(listOf("--sample-command"), emptyStringRegex)
        )
    }

    @Test(timeout=300_000)
    fun `Run CordaCliWrapper sample app with arguments and check error output matches regExp`() {
        val process = ProcessUtilities.startJavaProcess(className = className, arguments = arguments)

        process.waitFor()

        val processErrorOutput = BufferedReader(
                InputStreamReader(process.errorStream))
                .lines()
                .filter { it.contains("Exception") ||
                        it.contains("at ") ||
                        it.contains("exception") }
                .collect(Collectors.joining("\n"))
                .toString()

        assertThat(processErrorOutput, matchesPattern(outputRegexPattern))
    }
}
