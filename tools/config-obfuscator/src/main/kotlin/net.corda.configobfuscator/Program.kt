package net.corda.configobfuscator

import com.typesafe.config.ConfigFactory
import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.ExitCodes
import net.corda.cliutils.start
import net.corda.common.configuration.parsing.internal.ConfigObfuscator
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.event.Level
import picocli.CommandLine
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.Security

fun main(args: Array<String>) {
    Security.addProvider(BouncyCastleProvider())
    ConfigObfuscatorCli().apply {
        loggingLevel = Level.ERROR
        start(args)
    }
}

class ConfigObfuscatorCli : CordaCliWrapper(
        "config-obfuscator",
        "Command-line tool for obfuscating configuration files. An obfuscated file is usable by a Corda node " +
                "running on a machine with a hardware address matching the one used during obfuscation.\n\n" +
                "By default, the tool will obfuscate the provided configuration file and print the result to the terminal. " +
                "To write the result back to disk, use the -w command-line option."
) {
    // Do not expose to the end-user. Can be switched on for local testing by Corda developers.
    private var deobfuscate: Boolean = false

    @CommandLine.Option(
            names = ["-w"],
            description = ["Write the obfuscated output to disk, using the same file name as the input (if left blank), or the provided file name."],
            arity = "0..1")
    var writeToFile: Path? = null

    @CommandLine.Option(
            names = ["-i"],
            description = ["If set, provide input to obfuscated fields interactively."],
            arity = "0")
    var interactive: Boolean = false

    @CommandLine.Parameters(
            index = "0",
            paramLabel = "CONFIG_FILE",
            description = ["The configuration file to obfuscate."],
            arity = "1")
    var configFile: Path? = null

    @CommandLine.Parameters(
            index = "1",
            paramLabel = "HARDWARE_ADDRESS",
            description = ["The primary hardware address of the machine on which the configuration file resides. By default, " +
                    "the MAC address of the running machine will be used. Supplying 'DEFAULT' will explicitly use the default value."],
            defaultValue = "DEFAULT",
            showDefaultValue = CommandLine.Help.Visibility.NEVER,
            arity = "0..1")
    var hardwareAddress: String? = null

    @CommandLine.Parameters(
            index = "2",
            paramLabel = "SEED",
            description = ["Bytes seeding the encryption key used for obfuscation. Leave blank or supply 'DEFAULT' to use the default seed bytes."],
            defaultValue = "DEFAULT",
            showDefaultValue = CommandLine.Help.Visibility.NEVER,
            hidden = true,
            arity = "0..1")
    var seed: String? = null

    // Don't produce log file
    override fun call(): Int = runProgram()

    override fun runProgram(): Int {
        val useSameAsInput = (writeToFile == Paths.get("").toAbsolutePath())
        val outputFile = when {
            writeToFile != null && useSameAsInput -> configFile
            else -> writeToFile
        }

        val configFile = configFile
        if (configFile == null) {
            printHelp()
            return ExitCodes.FAILURE
        }

        if (!Files.exists(configFile)) {
            System.err.println("Error: Unable to find configuration file '$configFile}")
            return ExitCodes.FAILURE
        }

        val hardwareAddressBytes = if (!hardwareAddress.isNullOrBlank() && hardwareAddress != "DEFAULT") {
            try {
                hardwareAddress!!.split(':').map { Integer.parseInt(it, 16).toByte() }.toByteArray()
            } catch (_: Exception) {
                System.err.println("Error: Unable to parse the manually provided hardware address '$hardwareAddress'")
                return ExitCodes.FAILURE
            }
        } else {
            null
        }

        if (hardwareAddressBytes != null && verbose) {
            val list = hardwareAddressBytes.joinToString(":") { it.toPositiveInt().toString(16).padStart(2, '0') }
            System.err.println("Using hardware address: $list")
        }

        val seedBytes = if (!seed.isNullOrBlank() && seed != "DEFAULT") {
            try {
                seed!!.split(':').map { Integer.parseInt(it, 16).toByte() }.toByteArray()
            } catch (_: Exception) {
                System.err.println("Error: Unable to parse the manually provided seed '$seed'")
                return ExitCodes.FAILURE
            }
        } else {
            null
        }

        if (seedBytes != null && verbose) {
            val list = seedBytes.joinToString(":") { it.toPositiveInt().toString(16).padStart(2, '0') }
            System.err.println("Using seed bytes: $list")
        }

        val inputDelegate: ((String) -> String)? = if (interactive) {
            {
                System.err.print("Input for field '$it': ")
                System.err.flush()
                String(System.console().readPassword())
            }
        } else {
            null
        }

        try {
            val config = ConfigFactory.parseFile(configFile.toFile())
            config.resolve()
            if (verbose) {
                System.err.println("Successfully parsed and resolved configuration file")
            }
        } catch (ex: Exception) {
            System.err.println("Error: Failed to parse configuration file. ${ex.message}")
            return ExitCodes.FAILURE
        }

        val rawFields = mutableListOf<String>()
        val configContent = Files.readAllLines(configFile).joinToString("\n")
        val (fieldsProcessed, processedContent) = if (deobfuscate) {
            val result = ConfigObfuscator.deobfuscateConfiguration(configContent, hardwareAddressBytes, seedBytes)
            result.fieldCount to result.content
        } else {
            val result = ConfigObfuscator.obfuscateConfiguration(configContent, hardwareAddressBytes, seedBytes, inputDelegate)
            rawFields.addAll(result.rawFields)
            result.fieldCount to result.content
        }

        if (rawFields.any { it.contains("<encrypt{") }) {
            System.err.println("Error: Two obfuscation fields appear on the same line and consequently overlap.")
            System.err.println("       Please change the configuration so that a maximum of one field appears per line.")
            System.err.println();
            System.err.println("       Conflicting fields:")
            for (field in rawFields.filter { it.contains("<encrypt{") }) {
                System.err.println("        - $field")
            }
            return ExitCodes.FAILURE
        }

        if (writeToFile != null) {
            if (verbose) {
                System.err.println("Processed configuration written to '$outputFile'.")
            }
            Files.write(outputFile, processedContent.split("\n"))
        } else {
            println(processedContent)
        }

        if (fieldsProcessed == 0) {
            System.err.println("Warning: No fields found during ${if (deobfuscate) "deobfuscation" else "obfuscation"}.")
        } else if (verbose || writeToFile != null) {
            System.err.println("Found $fieldsProcessed field(s) during processing.")
        }

        if (verbose) {
            for (rawField in rawFields.filter { it.isNotBlank() }) {
                System.err.println(" - $rawField")
            }
        }

        return ExitCodes.SUCCESS
    }

    private fun Byte.toPositiveInt() = toInt() and 0xFF
}
