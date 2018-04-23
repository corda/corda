/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.process

import net.corda.behave.*
import net.corda.behave.file.currentDirectory
import net.corda.behave.logging.getLogger
import net.corda.behave.process.output.OutputListener
import rx.Observable
import rx.Subscriber
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.time.Duration
import java.util.concurrent.CountDownLatch

open class Command(
        private val command: List<String>,
        private val directory: File = currentDirectory,
        private val timeout: Duration = 2.minutes
): Closeable {

    protected val log = getLogger<Command>()

    private val terminationLatch = CountDownLatch(1)

    private val outputCapturedLatch = CountDownLatch(1)

    private var isInterrupted = false

    private var process: Process? = null

    private var outputListener: OutputListener? = null

    var exitCode = -1
        private set

    val output: Observable<String> = Observable.create<String>({ emitter ->
        outputListener = object : OutputListener {
            override fun onNewLine(line: String) {
                emitter.onNext(line)
            }

            override fun onEndOfStream() {
                emitter.onCompleted()
            }
        }
    }).share()

    private val thread = Thread(Runnable {
        try {
            log.info("Command: $command")
            val processBuilder = ProcessBuilder(command)
                    .directory(directory)
                    .redirectErrorStream(true)
            processBuilder.environment().putAll(System.getenv())
            process = processBuilder.start()
            val process = process!!
            Thread(Runnable {
                val input = process.inputStream.bufferedReader()
                while (true) {
                    try {
                        val line = input.readLine()?.trimEnd() ?: break
                        log.trace(line)
                        outputListener?.onNewLine(line)
                    } catch (_: IOException) {
                        break
                    } catch (ex: Exception) {
                        log.error("Unexpected exception during reading input", ex)
                        break
                    }
                }
                input.close()
                outputListener?.onEndOfStream()
                outputCapturedLatch.countDown()
            }).start()
            val streamIsClosed = outputCapturedLatch.await(timeout)
            val timeout = if (!streamIsClosed || isInterrupted) {
                1.second
            } else {
                timeout
            }
            if (!process.waitFor(timeout)) {
                process.destroy()
                process.waitFor(WAIT_BEFORE_KILL)
                if (process.isAlive) {
                    process.destroyForcibly()
                    process.waitFor()
                }
            }
            exitCode = process.exitValue()
            if (isInterrupted) {
                log.warn("Process ended after interruption")
            } else if (exitCode != 0 && exitCode != 143 /* SIGTERM */) {
                log.warn("Process {} ended with exit code {}", this, exitCode)
            }
        } catch (e: Exception) {
            log.warn("Error occurred when trying to run process", e)
            throw e
        }
        finally {
            process = null
            terminationLatch.countDown()
        }
    })

    fun start() {
        thread.start()
    }

    fun interrupt() {
        isInterrupted = true
        outputCapturedLatch.countDown()
    }

    fun waitFor(): Boolean {
        terminationLatch.await()
        return exitCode == 0
    }

    fun kill() {
        process?.destroy()
        process?.waitFor(WAIT_BEFORE_KILL)
        if (process?.isAlive == true) {
            process?.destroyForcibly()
        }
        if (process != null) {
            terminationLatch.await()
        }
        process = null
    }

    override fun close() {
        waitFor()
    }

    fun run() = use { _ -> }

    fun use(action: (Command) -> Unit): Int {
        try {
            start()
            action(this)
        } finally {
            close()
        }
        return exitCode
    }

    fun use(subscriber: Subscriber<String>, action: (Command, Observable<String>) -> Unit = { _, _ -> }): Int {
        try {
            output.subscribe(subscriber)
            start()
            action(this, output)
        } finally {
            close()
        }
        return exitCode
    }

    override fun toString() = "Command(${command.joinToString(" ")})"

    companion object {

        private val WAIT_BEFORE_KILL: Duration = 5.seconds

    }

}
