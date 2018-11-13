package net.corda.healthsurvey

import net.corda.healthsurvey.cli.Console
import net.corda.healthsurvey.cli.Console.yellow
import net.corda.healthsurvey.collectors.*
import net.corda.healthsurvey.output.Report
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun main(args: Array<String>) {
    printHeader()
    val canIncludeLogs = requestPermission("Can we include the node's log files in the report?")
    println()

    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault())

    val report = Report(Paths.get("report-${formatter.format(Instant.now())}.zip"))
    val collectors = collectors
            .add(canIncludeLogs, OptInLogCollector())
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

private val collectors = listOf(
        MachineInformationCollector(),
        CordaInfoCollector(),
        NetworkParametersCollector(),
        NodeInfoCollector(),
        AdditionalNodeInfoCollector(),
        CorDappCollector(),
        ConfigurationCollector(),
        NetworkStatusCollector(),
        DriverCollector()
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