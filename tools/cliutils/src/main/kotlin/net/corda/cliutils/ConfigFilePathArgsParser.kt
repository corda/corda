package net.corda.cliutils

import net.corda.core.internal.exists
import net.corda.core.internal.isReadable
import picocli.CommandLine
import java.nio.file.Path
import java.nio.file.Paths

/**
 * When a config file is required as part of setup, use this class to check that it exists and is formatted correctly. Add it as
 * `@CommandLine.Mixin
 * lateinit var configParser: ConfigFilePathArgsParser`
 * in your command class and then call `validate()`
 */
@CommandLine.Command(description = ["Parse configuration file. Checks if given configuration file exists"])
class ConfigFilePathArgsParser : Validated {
    @CommandLine.Option(names = ["--config-file", "-f"], paramLabel = "FILE", description = ["The path to the config file"])
    var configFile: Path = Paths.get("./node.conf")

    override fun validator(): List<String> {
        val res = mutableListOf<String>()
        if (!configFile.exists()) res += "Config file ${configFile.toAbsolutePath().normalize()} does not exist!"
        if (!configFile.isReadable) res += "Config file ${configFile.toAbsolutePath().normalize()} is not readable"
        return res
    }
}