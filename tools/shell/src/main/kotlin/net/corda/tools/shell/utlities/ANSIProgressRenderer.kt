package net.corda.tools.shell.utlities

import net.corda.core.internal.Emoji
import net.corda.core.messaging.FlowProgressHandle
import net.corda.core.utilities.loggerFor
import net.corda.tools.shell.utlities.StdoutANSIProgressRenderer.draw
import org.apache.commons.lang3.SystemUtils
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender
import org.apache.logging.log4j.core.appender.ConsoleAppender
import org.apache.logging.log4j.core.appender.OutputStreamManager
import org.crsh.text.RenderPrintWriter
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.Ansi.Attribute
import org.fusesource.jansi.AnsiConsole
import org.fusesource.jansi.AnsiOutputStream
import rx.Observable.combineLatest
import rx.Subscription
import java.util.*
import java.util.stream.IntStream
import kotlin.streams.toList

abstract class ANSIProgressRenderer {

    private var updatesSubscription: Subscription? = null

    protected var usingANSI = false
    protected var checkEmoji = false
    private val usingUnicode = !SystemUtils.IS_OS_WINDOWS

    private var treeIndex: Int = 0
    private var treeIndexProcessed: MutableSet<Int> = mutableSetOf()
    protected var tree: List<ProgressStep> = listOf()

    private var installedYet = false

    private var onDone: () -> Unit = {}

    // prevMessagePrinted is just for non-ANSI mode.
    private var prevMessagePrinted: String? = null
    // prevLinesDraw is just for ANSI mode.
    protected var prevLinesDrawn = 0

    data class ProgressStep(val level: Int, val description: String, val parentIndex: Int?)
    data class InputTreeStep(val level: Int, val description: String)

    private fun done(error: Throwable?) {
        if (error == null) renderInternal(null)
        draw(true, error)
        onDone()
    }

    fun render(flowProgressHandle: FlowProgressHandle<*>, onDone: () -> Unit = {}) {
        this.onDone = onDone
        renderInternal(flowProgressHandle)
    }

    protected abstract fun printLine(line:String)

    protected abstract fun printAnsi(ansi:Ansi)

    protected abstract fun setup()

    private fun renderInternal(flowProgressHandle: FlowProgressHandle<*>?) {
        updatesSubscription?.unsubscribe()
        treeIndex = 0
        treeIndexProcessed.clear()
        tree = listOf()

        if (!installedYet) {
            setup()
            installedYet = true
        }

        prevMessagePrinted = null
        prevLinesDrawn = 0
        draw(true)

        val treeUpdates = flowProgressHandle?.stepsTreeFeed?.updates
        val indexUpdates = flowProgressHandle?.stepsTreeIndexFeed?.updates

        if (treeUpdates == null || indexUpdates == null) {
            renderInBold("Cannot print progress for this flow as the required data is missing", Ansi())
        } else {
            // By combining the two observables, a race condition where both emit items at roughly the same time is avoided. This could
            // result in steps being incorrectly marked as skipped. Instead, whenever either observable emits an item, a pair of the
            // last index and last tree is returned, which ensures that updates to either are processed in series.
            updatesSubscription = combineLatest(treeUpdates, indexUpdates) { tree, index -> Pair(tree, index) }.subscribe(
                {
                    val newTree = transformTree(it.first.map { elem -> InputTreeStep(elem.first, elem.second) })
                    // Process indices first, as if the tree has changed the associated index with this update is for the old tree. Note
                    // that the one case where this isn't true is the very first update, but in this case the index should be 0 (as this
                    // update is for the initial state). The remapping on a new tree assumes the step at index 0 is always at least current,
                    // so this case is handled there.
                    treeIndex = it.second
                    treeIndexProcessed.add(it.second)
                    if (newTree != tree) {
                        remapIndices(newTree)
                        tree = newTree
                    }
                    draw(true)
                },
                { done(it) },
                { done(null) }
            )
        }
    }

    // Create a new tree of steps that also holds a reference to the parent of each step. This is required to uniquely identify each step
    // (assuming that each step label is unique at a given level).
    private fun transformTree(inputTree: List<InputTreeStep>): List<ProgressStep> {
        if (inputTree.isEmpty()) {
            return listOf()
        }
        val stack = Stack<Pair<Int, InputTreeStep>>()
        stack.push(Pair(0, inputTree[0]))
        return inputTree.mapIndexed { index, step ->
            val parentIndex = try {
                val top = stack.peek()
                val levelDifference = top.second.level - step.level
                if (levelDifference >= 0) {
                    // The top of the stack is at the same or lower level than the current step. Remove items from the top until the topmost
                    // item is at a higher level - this is the parent step.
                    repeat(levelDifference + 1) { stack.pop() }
                }
                stack.peek().first
            } catch (e: EmptyStackException) {
                // If there is nothing on the stack at any point, it implies that this step is at the top level and has no parent.
                null
            }
            stack.push(Pair(index, step))
            ProgressStep(step.level, step.description, parentIndex)
        }
    }

    private fun remapIndices(newTree: List<ProgressStep>) {
        val newIndices = newTree.filter {
            treeIndexProcessed.contains(tree.indexOf(it))
        }.map {
            newTree.indexOf(it)
        }.toMutableSet()
        treeIndex = newIndices.max() ?: 0
        treeIndexProcessed = if (newIndices.isNotEmpty()) newIndices else mutableSetOf(0)
    }

    @Synchronized protected fun draw(moveUp: Boolean, error: Throwable? = null) {

        if (!usingANSI) {
            val currentMessage = tree.getOrNull(treeIndex)?.description
            if (currentMessage != null && currentMessage != prevMessagePrinted) {
                printLine(currentMessage)
                prevMessagePrinted = currentMessage
            }
            return
        }

        fun printingBody() {
            // Handle the case where the number of steps in a progress tracker is changed during execution.
            val ansi = Ansi()
            if (prevLinesDrawn > 0 && moveUp)
                ansi.cursorUp(prevLinesDrawn)

            // Put a blank line between any logging and us.
            ansi.eraseLine()
            ansi.newline()
            if (tree.isEmpty()) return
            var newLinesDrawn = 1 + renderLevel(ansi, error != null)

            if (error != null) {
                val errorIcon = if (usingUnicode) Emoji.skullAndCrossbones else "ERROR: "

                var errorToPrint = error
                var indent = 0
                while (errorToPrint != null) {
                    ansi.fgRed()
                    ansi.a("${IntStream.range(indent, indent).mapToObj { "\t" }.toList().joinToString(separator = "") { s -> s }} $errorIcon ${error.message}")
                    ansi.reset()
                    errorToPrint = error.cause
                    indent++
                }
                ansi.eraseLine(Ansi.Erase.FORWARD)
                ansi.newline()
                newLinesDrawn++
            }

            if (newLinesDrawn < prevLinesDrawn) {
                // If some steps were removed from the progress tracker, we don't want to leave junk hanging around below.
                val linesToClear = prevLinesDrawn - newLinesDrawn
                repeat(linesToClear) {
                    ansi.eraseLine()
                    ansi.newline()
                }
                ansi.cursorUp(linesToClear)
            }
            prevLinesDrawn = newLinesDrawn

            printAnsi(ansi)
        }

        if (checkEmoji) {
            Emoji.renderIfSupported(::printingBody)
        } else {
            printingBody()
        }
    }

    // Returns number of lines rendered.
    private fun renderLevel(ansi: Ansi, error: Boolean): Int {
        with(ansi) {
            var lines = 0
            for ((index, step) in tree.withIndex()) {
                val processedStep = treeIndexProcessed.contains(index)
                val skippedStep = index < treeIndex && !processedStep
                val activeStep = index == treeIndex

                val marker = when {
                    activeStep -> if (usingUnicode) "${Emoji.rightArrow} " else "CURRENT: "
                    processedStep -> if (usingUnicode) " ${Emoji.greenTick} " else "DONE: "
                    skippedStep -> "      "
                    error -> if (usingUnicode) "${Emoji.noEntry} " else "ERROR: "
                    else -> "    "   // Not reached yet.
                }
                a("    ".repeat(step.level))
                a(marker)

                when {
                    activeStep -> renderInBold(step.description, ansi)
                    skippedStep -> renderInFaint(step.description, ansi)
                    else -> a(step.description)
                }

                eraseLine(Ansi.Erase.FORWARD)
                newline()
                lines++
            }
            return lines
        }
    }

    private fun renderInBold(payload: String, ansi: Ansi) {
        with(ansi) {
            a(Attribute.INTENSITY_BOLD)
            a(payload)
            a(Attribute.INTENSITY_BOLD_OFF)
        }
    }

    private fun renderInFaint(payload: String, ansi: Ansi) {
        with(ansi) {
            a(Attribute.INTENSITY_FAINT)
            a(payload)
            a(Attribute.INTENSITY_BOLD_OFF)
        }
    }

}

class CRaSHANSIProgressRenderer(val renderPrintWriter:RenderPrintWriter) : ANSIProgressRenderer() {

    override fun printLine(line: String) {
        renderPrintWriter.println(line)
    }

    override fun printAnsi(ansi: Ansi) {
        renderPrintWriter.print(ansi)
        renderPrintWriter.flush()
    }

    override fun setup() {
        // We assume SSH always use ANSI.
        usingANSI = true
    }


}

/**
 * Knows how to render a [FlowProgressHandle] to the terminal using coloured, emoji-fied output. Useful when writing small
 * command line tools, demos, tests etc. Just call [draw] method and it will go ahead and start drawing
 * if the terminal supports it. Otherwise it just prints out the name of the step whenever it changes.
 *
 * When a progress tracker is on the screen, it takes over the bottom part and reconfigures logging so that, assuming
 * 1 log event == 1 line, the progress tracker is always glued to the bottom and logging scrolls above it.
 *
 * TODO: More thread safety
 */
object StdoutANSIProgressRenderer : ANSIProgressRenderer() {

    override fun setup() {
        AnsiConsole.systemInstall()
        checkEmoji = true

        // This line looks weird as hell because the magic code to decide if we really have a TTY or not isn't
        // actually exposed anywhere as a function (weak sauce). So we have to rely on our knowledge of jansi
        // implementation details.
        usingANSI = AnsiConsole.wrapOutputStream(System.out) !is AnsiOutputStream

        if (usingANSI) {
            // This super ugly code hacks into log4j and swaps out its console appender for our own. It's a bit simpler
            // than doing things the official way with a dedicated plugin, etc, as it avoids mucking around with all
            // the config XML and lifecycle goop.
            val manager = LogManager.getContext(false) as LoggerContext
            val consoleAppender = manager.configuration.appenders.values.filterIsInstance<ConsoleAppender>().singleOrNull { it.name == "Console-Selector" }
            if (consoleAppender == null) {
                loggerFor<StdoutANSIProgressRenderer>().warn("Cannot find console appender - progress tracking may not work as expected")
                return
            }
            val scrollingAppender = object : AbstractOutputStreamAppender<OutputStreamManager>(
                    consoleAppender.name, consoleAppender.layout, consoleAppender.filter,
                    consoleAppender.ignoreExceptions(), true, consoleAppender.manager) {
                override fun append(event: LogEvent) {
                    // We lock on the renderer to avoid threads that are logging to the screen simultaneously messing
                    // things up. Of course this slows stuff down a bit, but only whilst this little utility is in use.
                    // Eventually it will be replaced with a real GUI and we can delete all this.
                    synchronized(StdoutANSIProgressRenderer) {
                        if (tree.isNotEmpty()) {
                            val ansi = Ansi.ansi()
                            repeat(prevLinesDrawn) { ansi.eraseLine().cursorUp(1).eraseLine() }
                            System.out.print(ansi)
                            System.out.flush()
                        }

                        super.append(event)

                        if (tree.isNotEmpty())
                            draw(false)
                    }
                }
            }
            scrollingAppender.start()
            manager.configuration.appenders[consoleAppender.name] = scrollingAppender
            val loggerConfigs = manager.configuration.loggers.values
            for (config in loggerConfigs) {
                val appenderRefs = config.appenderRefs
                val consoleAppenders = config.appenders.filter { it.value is ConsoleAppender }.keys
                consoleAppenders.forEach { config.removeAppender(it) }
                appenderRefs.forEach { config.addAppender(manager.configuration.appenders[it.ref], it.level, it.filter) }
            }
            manager.updateLoggers()
        }
    }

    override fun printLine(line:String) {
        System.out.println(line)
    }

    override fun printAnsi(ansi: Ansi) {
        // Need to force a flush here in order to ensure stderr/stdout sync up properly.
        System.out.print(ansi)
        System.out.flush()
    }
}