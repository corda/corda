@file:JvmName("ScenarioRunner")
package net.corda.behave.scenarios

import joptsimple.OptionParser
import kotlin.system.exitProcess

fun main(args: Array<out String>) {
    val parser = OptionParser()
    val featurePath = parser.accepts("path").withRequiredArg().required().ofType(String::class.java)
                            .describedAs("Path location of .feature specifications")
    val glue = parser.accepts("glue").withOptionalArg().ofType(String::class.java)
                            .describedAs("location of additional step definitions, hooks and plugins")
                            .defaultsTo("net.corda.behave.scenarios")
    val plugin = parser.accepts("plugin").withOptionalArg().ofType(String::class.java)
                            .describedAs("register additional plugins (see https://cucumber.io/docs/reference/jvm)")
                            .defaultsTo("pretty")
    val tags = parser.accepts("tags").withOptionalArg().ofType(String::class.java)
                            .describedAs("only run scenarios marked as @<tag-name>")
    val dryRun = parser.accepts("d")

    val options = try {
        parser.parse(*args)
    } catch (e: Exception) {
        println(e.message)
        printHelp(parser)
        exitProcess(1)
    }

    val cliArgs = listOf("--glue",
                        options.valueOf(glue),
                        "--plugin",
                        options.valueOf(plugin),
                        options.valueOf(featurePath)) +
                        (if (options.hasArgument("tags"))
                            listOf("--tags", options.valueOf(tags))
                        else emptyList()) +
                        if (options.has(dryRun)) listOf("-d") else emptyList()

    println("Cucumber CLI scenario runner args: $cliArgs")
    cucumber.api.cli.Main.main(cliArgs.toTypedArray())
}

private fun printHelp(parser: OptionParser) {
    println("""
        Usage: ScenarioRunner [options] --path <location of feature scenario definitions>

        Examples:
            ScenarioRunner -path <features-dir>
            ScenarioRunner -path <features-dir>/<name>.feature
            ScenarioRunner -path <features-dir>/<name>.feature:3:9

            ScenarioRunner -path <features-dir> --plugin html --tags @qa
            ScenarioRunner -path <features-dir> --plugin html --tags @compatibility

        Please refer to the Cucumber documentation https://cucumber.io/docs/reference/jvm for more info.

        """.trimIndent())
    parser.printHelpOn(System.out)
}