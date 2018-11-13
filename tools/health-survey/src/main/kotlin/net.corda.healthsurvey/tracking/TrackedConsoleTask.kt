package net.corda.healthsurvey.tracking

import net.corda.healthsurvey.cli.Console
import net.corda.healthsurvey.cli.Console.clearPreviousLine
import net.corda.healthsurvey.cli.FormattedMessage
import net.corda.healthsurvey.cli.MessageFormatter
import net.corda.healthsurvey.cli.TaskStatus
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

abstract class TrackedConsoleTask : Trackable {

    private val formatter = MessageFormatter()

    private var description = ""

    private var ellipsis = ""

    override fun step(description: String) {
        this.description = formatter.formatMessage(FormattedMessage(description, TaskStatus.IN_PROGRESS))
    }

    override fun complete(description: String) {
        this.description = formatter.formatMessage(FormattedMessage(description, TaskStatus.SUCCEEDED))
    }

    override fun fail(description: String) {
        this.description = formatter.formatMessage(FormattedMessage(description, TaskStatus.FAILED))
    }

    fun start() {
        if (Console.hasColours()) {
            startWithRichTerminal()
        } else {
            startWithoutRichTerminal()
        }
    }

    private fun startWithRichTerminal() {
        val semaphore = Semaphore(0)
        val completionSemaphore = Semaphore(0)
        thread(name = "[TrackedTask.CLI(${this.javaClass.simpleName})]") {
            println()
            var previousMessage = description
            while (true) {
                if (semaphore.tryAcquire(150, TimeUnit.MILLISECONDS)) {
                    break
                }
                evolveEllipsis(reset = previousMessage != description)
                clearPreviousLine()
                println("${description.replace("...", "").trimEnd()} $ellipsis")
                previousMessage = description
            }
            clearPreviousLine()
            println(description)
            completionSemaphore.release()
        }
        thread(name = "[TrackedTask.Worker(${this.javaClass.simpleName})]") {
            run()
            semaphore.release()
        }
        completionSemaphore.acquire()
    }

    private fun startWithoutRichTerminal() {
        val semaphore = Semaphore(0)
        val completionSemaphore = Semaphore(0)
        thread(name = "[TrackedTask.CLI(${this.javaClass.simpleName})]") {
            var previousMessage = description
            printLine(description)
            while (true) {
                if (semaphore.tryAcquire(150, TimeUnit.MILLISECONDS)) {
                    break
                }
                if (previousMessage != description) {
                    printLine(description)
                }
                previousMessage = description
            }
            if (previousMessage != description) {
                printLine(description)
            }
            completionSemaphore.release()
        }
        thread(name = "[TrackedTask.Worker(${this.javaClass.simpleName})]") {
            run()
            semaphore.release()
        }
        completionSemaphore.acquire()
    }

    private fun printLine(description: String) {
        if (description.isNotBlank()) {
            println(description)
        }
    }

    private fun isInProgress(description: String): Boolean {
        return !description.contains(Console.CROSS) && !description.contains(Console.TICK)
    }

    private fun evolveEllipsis(reset: Boolean = false) {
        ellipsis = if (!reset && ellipsis.length < 3 && description.isNotBlank()) { "$ellipsis." } else { "" }
    }

}
