package net.corda.testing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import picocli.CommandLine
import java.math.BigDecimal
import java.net.InetAddress
import java.util.*
import java.util.regex.Pattern


@CommandLine.Command(subcommands = [StatusCommand::class, ListCommand::class])
open class Dummy {
    @CommandLine.Option(names = arrayOf("-d", "--directory"), description = arrayOf("the directory to run in"))
    var baseDirectory: String? = null

    @CommandLine.Parameters(index = "0")
    var host: InetAddress? = null
    @CommandLine.Parameters(index = "1")
    var port: Int = 0
}

@CommandLine.Command(subcommands = [StatusCommandRemoved::class])
open class DummyRemoved {
    @CommandLine.Option(names = arrayOf("-d", "--directory"), description = arrayOf("the directory to run in"))
    var baseDirectory: String? = null

    @CommandLine.Parameters(index = "0")
    var host: InetAddress? = null
}

@CommandLine.Command(name = "status")
open class StatusCommand {
    @CommandLine.Option(names = arrayOf("-f", "--font"), description = arrayOf("font to use to print out"))
    var font: String? = null

    @CommandLine.Option(names = ["-p", "--pattern"], description = ["the regex patterns to use"])
    var patterns: Array<Pattern>? = null
}

@CommandLine.Command(name = "status")
open class StatusCommandRemoved {
    @CommandLine.Option(names = ["-p", "--pattern"], description = ["the regex patterns to use"])
    var patterns: Array<Pattern>? = null
}

@CommandLine.Command(name = "ls")
open class ListCommand {
    @CommandLine.Option(names = arrayOf("-d", "--depth"), description = arrayOf("the max level of recursion"))
    var depth: Int? = null

    @CommandLine.Parameters(index = "0")
    var machine: String? = null
    @CommandLine.Parameters(index = "1")
    var listStyle: Int = 0
}

@CommandLine.Command(name = "ls")
open class ListCommandRemoved {
    @CommandLine.Option(names = arrayOf("-d", "--depth"), description = arrayOf("the max level of recursion"))
    var depth: BigDecimal? = null

    @CommandLine.Parameters(index = "0")
    var machine: String? = null
}


fun topoSort(commandLine: CommandLine): List<CommandDescription> {
    val toVisit = Stack<CommandLine>()
    toVisit.push(commandLine)
    val sorted: MutableList<CommandLine> = ArrayList();
    while (toVisit.isNotEmpty()) {
        val visiting = toVisit.pop()
        sorted.add(visiting)
        visiting.subcommands.values.sortedBy { it.commandName }.forEach {
            toVisit.push(it)
        }
    }
    return buildDescriptors(sorted)
}

private fun buildDescriptors(result: MutableList<CommandLine>): List<CommandDescription> {
    return result.map { ::parseToDescription.invoke(it) }
}

private fun parseToDescription(it: CommandLine): CommandDescription {
    val commandSpec = it.commandSpec
    val options = commandSpec.options().filterNot { it.usageHelp() || it.versionHelp() }
            .map { hit -> hit.names().map { it to hit } }
            .flatMap { it }
            .sortedBy { it.first }
            .map {
                val type = it.second.type()
                ParameterDescription(it.first, type.componentType?.canonicalName
                        ?: type.canonicalName, it.second.required(), isMultiple(type))
            }

    val positionals = commandSpec.positionalParameters().sortedBy { it.index() }.map {
        val type = it.type()
        ParameterDescription(it.index().toString(), type.componentType?.canonicalName
                ?: type.canonicalName, it.required(), isMultiple(type))
    }
    return CommandDescription(it.commandName, positionals, options)
}

data class CommandDescription(val commandName: String, val positionalParams: List<ParameterDescription>, val params: List<ParameterDescription>)
data class ParameterDescription(val parameterName: String, val parameterType: String, val required: Boolean, val isMultiParam: Boolean)

fun isMultiple(clazz: Class<*>): Boolean {
    return Iterable::class.java.isAssignableFrom(clazz) || Array<Any>::class.java.isAssignableFrom(clazz)
}

fun printCommandDescription(commandLine: CommandLine) {
    val objectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
    val results = topoSort(commandLine)
    println(objectMapper.writeValueAsString(results))
}

fun checkAllCommandsArePresent(old: List<CommandDescription>, new: List<CommandDescription>): List<CliBackwardsCompatibilityValidationCheck> {
    val oldSet = old.map { it.commandName }.toSet()
    val newSet = new.map { it.commandName }.toSet()
    val newIsSuperSetOfOld = newSet.containsAll(oldSet)
    return if (!newIsSuperSetOfOld) {
        oldSet.filterNot { newSet.contains(it) }.map {
            CliBackwardsCompatibilityValidationCheck("SubCommand: $it has been removed from the CLI interface")
        }
    } else {
        emptyList()
    }
}

fun checkAllOptionsArePresent(old: CommandDescription, new: CommandDescription): List<CliBackwardsCompatibilityValidationCheck> {
    if (old.commandName != new.commandName) {
        throw IllegalArgumentException("Commands must match (${old.commandName} != ${new.commandName})")
    }
    val oldSet = old.params.sortedBy { it.parameterName }.toSet()
    val newSet = new.params.sortedBy { it.parameterName }.toSet()

    val newIsSuperSetOfOld = newSet.containsAll(oldSet)

    return if (!newIsSuperSetOfOld) {
        oldSet.filterNot { newSet.contains(it) }.map {
            CliBackwardsCompatibilityValidationCheck("Parameter: ${it.parameterName} has been removed from subcommand: ${old.commandName}")
        }
    } else {
        emptyList()
    }
}

fun checkAllPositionalCharactersArePresent(old: CommandDescription, new: CommandDescription): List<CliBackwardsCompatibilityValidationCheck> {
    if (old.commandName != new.commandName) {
        throw IllegalArgumentException("Commands must match (${old.commandName} != ${new.commandName})")
    }

    val oldSet = old.positionalParams.sortedBy { it.parameterName }.toSet()
    val newSet = new.positionalParams.sortedBy { it.parameterName }.toSet()

    val newIsSuperSetOfOld = newSet.containsAll(oldSet)

    return if (!newIsSuperSetOfOld) {
        oldSet.filterNot { newSet.contains(it) }.map {
            CliBackwardsCompatibilityValidationCheck("Positional Parameter [ ${it.parameterName} ] has been removed from subcommand: ${old.commandName}")
        }
    } else {
        emptyList()
    }
}

fun checkAllParamsAreOfTheSameType(old: CommandDescription, new: CommandDescription): List<CliBackwardsCompatibilityValidationCheck> {

    val oldMap = old.params.map { it.parameterName to it.parameterType }.toMap()
    val newMap = new.params.map { it.parameterName to it.parameterType }.toMap()

    return oldMap.filter { newMap[it.key] != it.value }.map {
        CliBackwardsCompatibilityValidationCheck("Parameter [ ${it.key} has changed from type: ${it.value} to ${newMap[it.key]}")
    }

}

data class CliBackwardsCompatibilityValidationCheck(val message: String)

fun main(args: Array<String>) {


    val commandLineOld = CommandLine(Dummy())
    val commandLineNew = CommandLine(DummyRemoved())

    val old = topoSort(commandLineOld)
    val new = topoSort(commandLineNew)

    println(checkAllCommandsArePresent(old, new))
    println(checkAllOptionsArePresent(parseToDescription(CommandLine(StatusCommand())), parseToDescription(CommandLine(StatusCommandRemoved()))));
    println(checkAllPositionalCharactersArePresent(parseToDescription(CommandLine(ListCommand())), parseToDescription(CommandLine(ListCommandRemoved()))));
    println(checkAllParamsAreOfTheSameType(parseToDescription(CommandLine(ListCommand())), parseToDescription(CommandLine(ListCommandRemoved()))));

}
