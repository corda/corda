package net.corda.healthsurvey

import net.corda.healthsurvey.cli.Console
import net.corda.healthsurvey.cli.Console.yellow
import net.corda.healthsurvey.collectors.*
import net.corda.healthsurvey.domain.NodeConfigurationPaths
import net.corda.healthsurvey.output.Report
import org.apache.commons.cli.*
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun main(args: Array<String>) {
    val nodeConfigPaths = args.readNodeConfigPaths() ?: return
    val nonExistentPaths = nodeConfigPaths.requiredExisting.filter { path -> !path.toFile().exists() }.toSet()
    if (nonExistentPaths.isNotEmpty()) {
        println(" ${yellow("Error:")} Some specified configuration paths do not point to existing files or directories. Paths were: ${nonExistentPaths.joinToString(separator = ", ", prefix = "[", postfix = "]")}")
        return
    }

    printHeader()
    val canIncludeLogs = requestPermission("Can we include the node's log files in the report?")
    println()

    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault())

    val report = Report(Paths.get("report-${formatter.format(Instant.now())}.zip"))
    val collectors = collectors(nodeConfigPaths)
            .add(canIncludeLogs, OptInLogCollector(nodeConfigPaths.logs))
            .add(ExportReportJob())

    collectors.forEach { collector ->
        collector.start(report)
        if (!Console.hasColours()) {
            println()
        }
    }

    if (Console.hasColours()) {
        println()
    }
    println(" A report has been generated and written to disk. Please send the file to the")
    println(" R3 Support Team by e-mail (${yellow("support@r3.com")}) or upload it to your JIRA support")
    println(" ticket for further assistance.")
    println()
    println(" Path of report: ${yellow(report.path.toAbsolutePath())}")
    println(" Size of report: ${yellow(humaniseBytes(Files.size(report.path)))}")
    println()
    println(" If you already have a support ticket in JIRA, we can attempt to upload the")
    val uploadToJira = requestPermission("report directly to the ticket. Would you like to try that?")

    if (uploadToJira) {
        println()
        val ticket = getTicket() ?: return
        val (username, password) = getUsernameAndPassword() ?: return
        println()
        AttachmentUploaderJob(username, password, ticket).start(report)
    }

    println()
}

private const val version = "1.0"

private fun collectors(nodeConfigurationPaths: NodeConfigurationPaths) = listOf(
        MachineInformationCollector(),
        CordaInfoCollector(nodeConfigurationPaths.baseDirectory),
        NetworkParametersCollector(nodeConfigurationPaths.networkParameters),
        NodeInfoCollector(nodeConfigurationPaths.baseDirectory),
        AdditionalNodeInfoCollector(nodeConfigurationPaths.additionalNodeInfos),
        CorDappCollector(nodeConfigurationPaths.cordapps),
        ConfigurationCollector(nodeConfigurationPaths.nodeConfigurationFile),
        NetworkStatusCollector(nodeConfigurationPaths.nodeConfigurationFile),
        DriverCollector(nodeConfigurationPaths.drivers)
)

private fun getUsernameAndPassword(): Pair<String, String>? {
    val usernameFromEnvironment = System.getenv("JIRA_USER") ?: ""
    val passwordFromEnvironment = System.getenv("JIRA_PASSWORD") ?: ""
    if (usernameFromEnvironment.isNotBlank() && passwordFromEnvironment.isNotBlank()) {
        return Pair(usernameFromEnvironment, passwordFromEnvironment)
    }

    val username = getInput(" JIRA username: ")
    if (username.isBlank()) {
        println(" ${yellow("Error:")} No username provided.")
        println()
        return null
    }
    val password = getInput(" JIRA password: ", true)
    if (password.isBlank()) {
        println(" ${yellow("Error:")} No password provided.")
        println()
        return null
    }
    return Pair(username, password)
}

private fun getTicket(): String? {
    val ticket = getInput(" JIRA ticket number: ")
    if (ticket.isBlank()) {
        println(" ${yellow("Error:")} No ticket number provided.")
        println()
        return null
    }
    return ticket
}

private fun getInput(prompt: String, isPassword: Boolean = false): String {
    print(prompt)
    val console = System.console()
    return if (isPassword) {
        String(console.readPassword())
    } else {
        readLine() ?: ""
    }
}

private fun printHeader() {
    println()
    println(" Corda Health Survey Tool $version")
    println(" ~~~~~~~~~~~~~~~~~~~~~~~~~~~~")
    println()
}

private fun requestPermission(question: String): Boolean {
    print(" $question [yes/no] ")
    return readLine()?.toLowerCase()?.startsWith('y') ?: false
}

private fun humaniseBytes(bytes: Long, si: Boolean = false): String {
    val unit = if (si) 1000 else 1024
    if (bytes < unit) return bytes.toString() + " B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
    val pre = (if (si) "kMGTPE" else "KMGTPE")[exp - 1] + if (si) "" else "i"
    return String.format("%.1f %sB", bytes / Math.pow(unit.toDouble(), exp.toDouble()), pre)
}

private fun <E> List<E>.add(element: E): List<E> {
    return this + element
}

private fun <E> List<E>.add(condition: Boolean, element: E): List<E> {
    return if (condition) {
        this + element
    } else {
        this
    }
}

private fun Array<String>.readNodeConfigPaths(): NodeConfigurationPaths? {
    val defaultBaseDirectory = Paths.get(".")
    val options = Options()
    val baseDirectoryArg = option("d", "base-directory", "Path to the Corda node base directory")
    options += baseDirectoryArg

    val nodeConfigurationArg = option("c", "node-configuration", "Path to the Corda node configuration file")
    options += nodeConfigurationArg

    val parser: CommandLineParser = DefaultParser()
    val optionsFormatter = HelpFormatter()
    val cmd: CommandLine? = try {
        parser.parse(options, this)
    } catch (e: ParseException) {
        println(e.message)
        optionsFormatter.printHelp("Corda Health Survey Tool", options)
        null
    }
    return cmd?.let {
        val baseDirectory = cmd[baseDirectoryArg]?.let { path -> Paths.get(path) } ?: defaultBaseDirectory
        val nodeConfigurationPath = cmd[nodeConfigurationArg]?.let { path -> Paths.get(path) }

        return NodeConfigurationPaths(baseDirectory.toAbsolutePath(), nodeConfigurationPath?.toAbsolutePath())
    }
}

private fun option(opt: String, longOpt: String, description: String, hasArg: Boolean = true, isRequired: Boolean = false, type: Class<*> = String::class.java): Option {
    val option = Option(opt, longOpt, hasArg, description)
    option.isRequired = isRequired
    option.setType(type)
    return option
}

private operator fun Options.plusAssign(option: Option) {
    addOption(option)
}

private operator fun CommandLine.get(option: Option): String? = getOptionValue(option.longOpt)