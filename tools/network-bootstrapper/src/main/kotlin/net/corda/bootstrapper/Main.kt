@file:JvmName("Main")

package net.corda.bootstrapper

import javafx.application.Application
import net.corda.bootstrapper.backends.Backend
import net.corda.bootstrapper.backends.Backend.BackendType.AZURE
import net.corda.bootstrapper.cli.AzureParser
import net.corda.bootstrapper.cli.CliParser
import net.corda.bootstrapper.cli.CommandLineInterface
import net.corda.bootstrapper.docker.DockerUtils
import net.corda.bootstrapper.gui.Gui
import net.corda.bootstrapper.serialization.SerializationEngine
import picocli.CommandLine
import javax.ws.rs.ProcessingException
import kotlin.system.exitProcess

val baseArgs = CliParser()

fun main(args: Array<String>) {
    SerializationEngine.init()
    CommandLine(baseArgs).parse(*args)
    testDockerConnectivity()

    if (baseArgs.gui) {
        Application.launch(Gui::class.java)
        return
    }

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

private fun testDockerConnectivity() {
    try {
        DockerUtils.createLocalDockerClient().listImagesCmd().exec()
    } catch (se: ProcessingException) {
        if (baseArgs.gui) {
            GuiUtils.showAndQuit("Could not connect to Docker", "Please ensure that docker is running locally", null)
        } else {
            System.err.println("Could not connect to Docker, please ensure that docker is running locally")
            exitProcess(1)
        }
    }
}
