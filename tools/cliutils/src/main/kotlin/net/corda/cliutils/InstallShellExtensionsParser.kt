package net.corda.cliutils

import net.corda.core.internal.*
import net.corda.core.utilities.loggerFor
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.SystemUtils
import picocli.CommandLine
import picocli.CommandLine.Command
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*
import net.corda.common.logging.CordaVersion

private class ShellExtensionsGenerator(val parent: CordaCliWrapper) {
    private companion object {
        private const val minSupportedBashVersion = 4
    }

    private class SettingsFile(val filePath: Path) {
        private val lines: MutableList<String> by lazy { getFileLines() }
        var fileModified: Boolean = false

        // Return the lines in the file if it exists, else return an empty mutable list
        private fun getFileLines(): MutableList<String> {
            return if (filePath.exists()) {
                filePath.toFile().readLines().toMutableList()
            } else {
                Collections.emptyList<String>().toMutableList()
            }
        }

        fun addOrReplaceIfStartsWith(startsWith: String, replaceWith: String) {
            val index = lines.indexOfFirst { it.startsWith(startsWith) }
            if (index >= 0) {
                if (lines[index] != replaceWith) {
                    lines[index] = replaceWith
                    fileModified = true
                }
            } else {
                lines.add(replaceWith)
                fileModified = true
            }
        }

        fun addIfNotExists(line: String) {
            if (!lines.contains(line)) {
                lines.add(line)
                fileModified = true
            }
        }

        fun updateAndBackupIfNecessary() {
            if (fileModified) {
                val backupFilePath = filePath.parent / "${filePath.fileName}.backup"
                println("Updating settings in ${filePath.fileName} - existing settings file has been backed up to $backupFilePath")
                if (filePath.exists()) filePath.copyTo(backupFilePath, StandardCopyOption.REPLACE_EXISTING)
                filePath.writeLines(lines)
            }
        }
    }

    private val userHome: Path by lazy { Paths.get(System.getProperty("user.home")) }
    private val jarLocation: Path by lazy {
        val capsuleJarProperty = System.getProperty("capsule.jar")
        if (capsuleJarProperty != null) {
            Paths.get(capsuleJarProperty)
        } else {
            this.javaClass.location.toPath()
        }
    }

    // If on Windows, Path.toString() returns a path with \ instead of /, but for bash Windows users we want to convert those back to /'s
    private fun Path.toStringWithDeWindowsfication(): String = this.toAbsolutePath().toString().replace("\\", "/")

    private fun jarVersion(alias: String) = "# $alias - Version: ${CordaVersion.releaseVersion}, Revision: ${CordaVersion.revision}"
    private fun getAutoCompleteFileLocation(alias: String) = userHome / ".completion" / alias

    private fun generateAutoCompleteFile(alias: String) {
        println("Generating $alias auto completion file")
        val autoCompleteFile = getAutoCompleteFileLocation(alias)
        autoCompleteFile.parent.createDirectories()
        val hierarchy = CommandLine(parent)
        parent.subCommands.forEach { hierarchy.addSubcommand(it.alias, it)}

        val builder = StringBuilder(picocli.AutoComplete.bash(alias, hierarchy))
        builder.append(jarVersion(alias))
        autoCompleteFile.writeText(builder.toString())
    }

    fun installShellExtensions(): Int {
        // Get jar location and generate alias command
        val command = "alias ${parent.alias}='java -jar \"${jarLocation.toStringWithDeWindowsfication()}\"'"
        var generateAutoCompleteFile = true
        if (SystemUtils.IS_OS_UNIX && installedShell() == ShellType.BASH) {
            val semanticParts = declaredBashVersion().split(".")
            semanticParts.firstOrNull()?.toIntOrNull()?.let { major ->
                if (major < minSupportedBashVersion) {
                    printWarning("Cannot install shell extension for bash major version earlier than $minSupportedBashVersion. Please upgrade your bash version. Aliases should still work.")
                    generateAutoCompleteFile = false
                }
            }
        }
        if (generateAutoCompleteFile) {
            generateAutoCompleteFile(parent.alias)
        }

        // Get bash settings file
        val bashSettingsFile = SettingsFile(userHome / ".bashrc")
        // Replace any existing alias. There can be only one.
        bashSettingsFile.addOrReplaceIfStartsWith("alias ${parent.alias}", command)
        val completionFileCommand = "for bcfile in ~/.completion/* ; do . \$bcfile; done"
        if (generateAutoCompleteFile) {
            bashSettingsFile.addIfNotExists(completionFileCommand)
        }
        bashSettingsFile.updateAndBackupIfNecessary()

        // Get zsh settings file
        val zshSettingsFile = SettingsFile(userHome / ".zshrc")
        zshSettingsFile.addIfNotExists("autoload -U +X compinit && compinit")
        zshSettingsFile.addIfNotExists("autoload -U +X bashcompinit && bashcompinit")
        zshSettingsFile.addOrReplaceIfStartsWith("alias ${parent.alias}", command)
        if (generateAutoCompleteFile) {
            zshSettingsFile.addIfNotExists(completionFileCommand)
        }
        zshSettingsFile.updateAndBackupIfNecessary()

        if (generateAutoCompleteFile) {
            println("Installation complete, ${parent.alias} is available in bash with autocompletion.")
        } else {
            println("Installation complete, ${parent.alias} is available in bash, but autocompletion was not installed because of an old version of bash.")
        }
        println("Type `${parent.alias} <options>` from the commandline.")
        println("Restart bash for this to take effect, or run `. ~/.bashrc` in bash or `. ~/.zshrc` in zsh to re-initialise your shell now")
        return ExitCodes.SUCCESS
    }

    private fun declaredBashVersion(): String = execCommand("bash", "-c", "echo \$BASH_VERSION")

    private fun installedShell(): ShellType {
        val path = execCommand("bash", "-c", "echo \$SHELL").trim()
        return when {
            path.endsWith("/zsh") -> ShellType.ZSH
            path.endsWith("/bash") -> ShellType.BASH
            else -> ShellType.OTHER
        }
    }

    private enum class ShellType {
        ZSH, BASH, OTHER
    }

    private fun execCommand(vararg commandAndArgs: String): String {
        return try {
            val process = ProcessBuilder(*commandAndArgs)
            IOUtils.toString(process.start().inputStream, Charsets.UTF_8)
        } catch (exception: Exception) {
            loggerFor<InstallShellExtensionsParser>().warn("Failed to run command: ${commandAndArgs.joinToString(" ")}; $exception")
            ""
        }
    }

    fun checkForAutoCompleteUpdate() {
        val autoCompleteFile = getAutoCompleteFileLocation(parent.alias)

        // If no autocomplete file, it hasn't been installed, so don't do anything
        if (!autoCompleteFile.exists()) return

        var lastLine = ""
        autoCompleteFile.toFile().forEachLine { lastLine = it }

        if (lastLine != jarVersion(parent.alias)) {
            println("Old auto completion file detected... regenerating")
            generateAutoCompleteFile(parent.alias)
            println("Restart bash for this to take effect, or run `. ~/.bashrc` to re-initialise bash now")
        }
    }
}

@Command(helpCommand = true)
class InstallShellExtensionsParser(private val cliWrapper: CordaCliWrapper) : CliWrapperBase("install-shell-extensions", "Install alias and autocompletion for bash and zsh") {
    private val generator = ShellExtensionsGenerator(cliWrapper)
    override fun runProgram(): Int {
        return generator.installShellExtensions()
    }

    fun updateShellExtensions() = generator.checkForAutoCompleteUpdate()
}