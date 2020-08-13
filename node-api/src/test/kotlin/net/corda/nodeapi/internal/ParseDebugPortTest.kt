package net.corda.nodeapi.internal

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ParseDebugPortTest(private val args: Iterable<String>,
                         private val expectedPort: Short?,
                         @Suppress("unused_parameter") description : String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{2}")
        fun load() = arrayOf(
                arrayOf(emptyList<String>(), null, "No arguments"),
                arrayOf(listOf("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=1234"), 1234.toShort(), "Debug argument"),
                arrayOf(listOf("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=0.0.0.0:7777"), 7777.toShort(), "Debug argument with bind address"),
                arrayOf(listOf("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y"), null, "Debug argument without port"),
                arrayOf(listOf("-version", "-Dmy.jvm.property=someValue"), null, "Unrelated arguments"),
                arrayOf(listOf("-Dcapsule.jvm.args=\"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=4321",
                        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=1234"), 1234.toShort(), "Debug argument and capsule arguments")
        )
    }

    @Test(timeout = 10_000)
    fun test() {
        val port = JVMAgentUtilities.parseDebugPort(args)
        Assert.assertEquals(expectedPort, port)
    }
}