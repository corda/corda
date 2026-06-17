package net.corda.testing.node.internal

import net.corda.testing.internal.IS_OPENJ9
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.matchesPattern
import org.junit.Assume
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
        // For openj9 the process error output appears sometimes to be garbled.
        Assume.assumeTrue(!IS_OPENJ9)
        val process = ProcessUtilities.startJavaProcess(
                className = className,
                arguments = arguments,
                inheritIO = false)

        process.waitFor()

        val processErrorOutput = BufferedReader(
                InputStreamReader(process.errorStream))
                .lines()
                .filter { !it.startsWith("Warning: Nashorn") }
                // Log4j 2.17.2+ disables scripting by default, producing this error when log4j2-test.xml
                // tries to use ScriptPatternSelector. Filter it out as it's not relevant to CLI error handling.
                .filter { !it.contains("Script support is not enabled") }
                .collect(Collectors.joining("\n"))
                .toString()

        assertThat(processErrorOutput, matchesPattern(outputRegexPattern))
    }
}