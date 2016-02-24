/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.utilities

import org.fusesource.jansi.Ansi
import org.fusesource.jansi.AnsiConsole
import org.fusesource.jansi.AnsiOutputStream
import rx.Subscription
import java.util.logging.ConsoleHandler
import java.util.logging.Formatter
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * Knows how to render a [ProgressTracker] to the terminal using coloured, emoji-fied output. Useful when writing small
 * command line tools, demos, tests etc. Just set the [progressTracker] field and it will go ahead and start drawing
 * if the terminal supports it. Otherwise it just prints out the name of the step whenever it changes.
 *
 * TODO: Thread safety
 */
object ANSIProgressRenderer {
    private var installedYet = false
    private var subscription: Subscription? = null

    private class LineBumpingConsoleHandler : ConsoleHandler() {
        override fun getFormatter(): Formatter = BriefLogFormatter()

        override fun publish(r: LogRecord?) {
            if (progressTracker != null) {
                val ansi = Ansi.ansi()
                repeat(prevLinesDrawn) { ansi.eraseLine().cursorUp(1).eraseLine() }
                System.out.print(ansi)
                System.out.flush()
            }

            super.publish(r)

            if (progressTracker != null)
                draw(false)
        }
    }

    private var usingANSI = false
    private var loggerRef: Logger? = null

    var progressTracker: ProgressTracker? = null
        set(value) {
            subscription?.unsubscribe()

            field = value
            if (!installedYet) {
                AnsiConsole.systemInstall()

                // This line looks weird as hell because the magic code to decide if we really have a TTY or not isn't
                // actually exposed anywhere as a function (weak sauce). So we have to rely on our knowledge of jansi
                // implementation details.
                usingANSI = AnsiConsole.wrapOutputStream(System.out) !is AnsiOutputStream

                if (usingANSI) {
                    loggerRef = Logger.getLogger("").apply {
                        val current = handlers[0]
                        removeHandler(current)
                        val new = LineBumpingConsoleHandler()
                        new.level = current.level
                        addHandler(new)
                    }
                }

                installedYet = true
            }

            subscription = value?.changes?.subscribe { draw(true) }
        }

    // prevMessagePrinted is just for non-ANSI mode.
    private var prevMessagePrinted: String? = null
    // prevLinesDraw is just for ANSI mode.
    private var prevLinesDrawn = 0

    private fun draw(moveUp: Boolean) {
        val pt = progressTracker!!

        if (!usingANSI) {
            val currentMessage = pt.currentStepRecursive.label
            if (currentMessage != prevMessagePrinted) {
                println(currentMessage)
                prevMessagePrinted = currentMessage
            }
            return
        }

        // Handle the case where the number of steps in a progress tracker is changed during execution.
        val ansi = Ansi.ansi()
        if (prevLinesDrawn > 0 && moveUp)
            ansi.cursorUp(prevLinesDrawn)

        // Put a blank line between any logging and us.
        ansi.eraseLine()
        ansi.newline()
        val newLinesDrawn = 1 + pt.renderLevel(ansi, 0, pt.allSteps)
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

        // Need to force a flush here in order to ensure stderr/stdout sync up properly.
        System.out.print(ansi)
        System.out.flush()
    }

    // Returns number of lines rendered.
    private fun ProgressTracker.renderLevel(ansi: Ansi, indent: Int, allSteps: List<Pair<Int, ProgressTracker.Step>>): Int {
        with(ansi) {
            var lines = 0
            for ((index, step) in steps.withIndex()) {
                // Don't bother rendering these special steps in some cases.
                if (step == ProgressTracker.UNSTARTED) continue
                if (indent > 0 && step == ProgressTracker.DONE) continue

                val marker = when {
                    index < stepIndex -> Emoji.CODE_GREEN_TICK + "  "
                    index == stepIndex && step == ProgressTracker.DONE -> Emoji.CODE_GREEN_TICK + "  "
                    index == stepIndex -> Emoji.CODE_RIGHT_ARROW + "  "
                    else -> "   "
                }
                a("    ".repeat(indent))
                a(marker)

                val active = index == stepIndex && step != ProgressTracker.DONE
                if (active) bold()
                a(step.label)
                if (active) boldOff()

                eraseLine(Ansi.Erase.FORWARD)
                newline()
                lines++

                val child = childrenFor[step]
                if (child != null)
                    lines += child.renderLevel(ansi, indent + 1, allSteps)
            }
            return lines
        }
    }
}