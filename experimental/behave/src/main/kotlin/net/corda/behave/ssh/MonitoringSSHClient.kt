/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.ssh

import net.corda.behave.process.output.OutputListener
import rx.Observable
import java.io.Closeable
import java.io.InterruptedIOException

class MonitoringSSHClient(
        private val client: SSHClient
) : Closeable {

    private var isRunning = false

    private lateinit var outputListener: OutputListener

    val output: Observable<String> = Observable.create<String> { emitter ->
        outputListener = object : OutputListener {
            override fun onNewLine(line: String) {
                emitter.onNext(line)
            }

            override fun onEndOfStream() {
                emitter.onCompleted()
            }
        }
    }

    private val thread = Thread(Runnable {
        while (isRunning) {
            try {
                val line = client.readLine() ?: break
                outputListener.onNewLine(line)
            } catch (_: InterruptedIOException) {
                break
            }
        }
        outputListener.onEndOfStream()
    })

    init {
        isRunning = true
        output.subscribe()
        thread.start()
    }

    override fun close() {
        isRunning = false
        thread.join(1000)
        if (thread.isAlive) {
            thread.interrupt()
        }
        client.close()
    }

    fun use(action: (MonitoringSSHClient) -> Unit) {
        try {
            action(this)
        } finally {
            close()
        }
    }

    fun write(vararg bytes: Byte) = client.write(*bytes)

    fun write(charSequence: CharSequence) = client.write(charSequence)

    fun writeLine(string: String) = client.writeLine(string)

}