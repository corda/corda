/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.tools.shell.utlities

import net.corda.core.internal.Emoji
import net.corda.core.messaging.FlowProgressHandle
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender
import org.apache.logging.log4j.core.appender.ConsoleAppender
import org.apache.logging.log4j.core.appender.OutputStreamManager
import org.crsh.text.RenderPrintWriter
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.AnsiConsole
import org.fusesource.jansi.AnsiOutputStream
import rx.Subscription

abstract class ANSIProgressRenderer {

    private var subscriptionIndex: Subscription? = null
    private var subscriptionTree: Subscription? = null

    protected var usingANSI = false
    protected var checkEmoji = false

    protected var treeIndex: Int = 0
    protected var tree: List<Pair<Int,String>> = listOf()

    private var installedYet = false

    private var onDone: () -> Unit = {}

    // prevMessagePrinted is just for non-ANSI mode.
    private var prevMessagePrinted: String? = null
    // prevLinesDraw is just for ANSI mode.
    protected var prevLinesDrawn = 0

    private fun done(error: Throwable?) {
        if (error == null) _render(null)
        draw(true, error)
        onDone()
    }

    fun render(flowProgressHandle: FlowProgressHandle<*>, onDone: () -> Unit = {}) {
        this.onDone = onDone
        _render(flowProgressHandle)
    }

    protected abstract fun printLine(line:String)

    protected abstract fun printAnsi(ansi:Ansi)

    protected abstract fun setup()

    private fun _render(flowProgressHandle: FlowProgressHandle<*>?) {
        subscriptionIndex?.unsubscribe()
        subscriptionTree?.unsubscribe()
        treeIndex = 0
        tree = listOf()

        if (!installedYet) {
            setup()
            installedYet = true
        }

        prevMessagePrinted = null
        prevLinesDrawn = 0
        draw(true)


        flowProgressHandle?.apply {
            stepsTreeIndexFeed?.apply {
                treeIndex = snapshot
                subscriptionIndex = updates.subscribe({
                    treeIndex = it
                    draw(true)
                }, { done(it) }, { done(null) })
            }
            stepsTreeFeed?.apply {
                tree = snapshot
                subscriptionTree = updates.subscribe({
                    tree = it
                    draw(true)
                }, { done(it) }, { done(null) })
            }
        }
    }



    @Synchronized protected fun draw(moveUp: Boolean, error: Throwable? = null) {
        if (!usingANSI) {
            val currentMessage = tree.getOrNull(treeIndex)?.second
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
                ansi.a("${Emoji.skullAndCrossbones} ${error.message}")
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

                val marker = when {
                    index < treeIndex -> "${Emoji.greenTick} "
                    treeIndex == tree.lastIndex -> "${Emoji.greenTick} "
                    index == treeIndex -> "${Emoji.rightArrow} "
                    error -> "${Emoji.noEntry} "
                    else -> "    "   // Not reached yet.
                }
                a("    ".repeat(step.first))
                a(marker)

                val active = index == treeIndex
                if (active) bold()
                a(step.second)
                if (active) boldOff()

                eraseLine(Ansi.Erase.FORWARD)
                newline()
                lines++
            }
            return lines
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
            val consoleAppender = manager.configuration.appenders.values.filterIsInstance<ConsoleAppender>().single { it.name == "Console-Appender" }
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
