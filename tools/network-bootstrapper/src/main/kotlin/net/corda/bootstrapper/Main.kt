@file:JvmName("Main")
package net.corda.bootstrapper

import javafx.application.Application
import net.corda.bootstrapper.backends.Backend
import net.corda.bootstrapper.backends.Backend.BackendType.AZURE
import net.corda.bootstrapper.cli.AzureParser
import net.corda.bootstrapper.cli.CliParser
import net.corda.bootstrapper.cli.CommandLineInterface
import net.corda.bootstrapper.gui.Gui
import net.corda.bootstrapper.serialization.SerializationEngine
import picocli.CommandLine

fun main(args: Array<String>) {
    SerializationEngine.init()
    if (args.isEmpty()) {
        Application.launch(Gui::class.java, *args)
    } else if (args.first() == "--help" || args.first() == "-h") {
        CommandLine.usage(AzureParser(), System.out)
    } else {
        val baseArgs = CliParser()
        CommandLine(baseArgs).parse(*args)
        val argParser: CliParser = when (baseArgs.backendType) {
            AZURE -> {
                val azureArgs = AzureParser()
                CommandLine(azureArgs).parse(*args)
                azureArgs
            }
            Backend.BackendType.LOCAL_DOCKER -> baseArgs
        }
        CommandLineInterface().run(argParser)
    }
}
