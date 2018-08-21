package net.corda.cliutils

import net.corda.core.internal.exists
import net.corda.core.internal.isReadable
import picocli.CommandLine
import java.nio.file.Path

/**
 * Something heavily used in network services, I am not sure it's of much use in corda, but who knows. Definitely it was the key to making DevOps happy.
 * Add it as
 * `@CommandLine.Mixin
 * lateinit var configParser: ConfigFilePathArgsParser`
 *
 * in your command class and then validate()
 */
@CommandLine.Command(description = ["Parse configuration file. Checks if given configuration file exists"])
class ConfigFilePathArgsParser : Validated {
    @CommandLine.Option(names = ["--config-file", "-f"], required = true, paramLabel = "FILE", description = ["The path to the config file"])
    lateinit var configFile: Path

    override fun validator(): List<String> {
        val res = mutableListOf<String>()
        if (!configFile.exists()) res += "Config file ${configFile.toAbsolutePath().normalize()} does not exist!"
        if (!configFile.isReadable) res += "Config file ${configFile.toAbsolutePath().normalize()} is not readable"
        return res
    }
}