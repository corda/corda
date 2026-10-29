package net.corda.testing.node.internal


import net.corda.cliutils.ExitCodes
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import kotlin.test.assertEquals
@RunWith(value = Parameterized::class)
class RunCordaNodeReturnCodeTests(val argument: String, val exitCode: Int){

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data() = listOf(
                arrayOf<Any>("--nonExistingOption", ExitCodes.FAILURE),
                arrayOf<Any>("--help", ExitCodes.SUCCESS),
                arrayOf<Any>("validate-configuration", ExitCodes.FAILURE),//Should fail as there is no node.conf
                arrayOf<Any>("initial-registration", ExitCodes.FAILURE) //Missing required option
        )
    }

    @Test(timeout=300_000)
    fun runCordaWithArgumentAndAssertExitCode() {

        val process = ProcessUtilities.startJavaProcess(
                className = "net.corda.node.Corda",
                arguments = listOf(argument)
        )
        process.waitFor()
        assertEquals(exitCode, process.exitValue())
    }


}