/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.demobench.pty

import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Removes any zero byte values from the output stream.
 * This stream is connected to a terminal's STDIN, and
 * any zeros received will trigger unwanted key mappings.
 * JavaFX seems to be inserting these zeros into the
 * stream as it tries to inter-operate with Swing.
 */
private class ZeroFilter(output: OutputStream) : FilterOutputStream(output) {

    @Throws(IOException::class)
    override fun write(b: Int) {
        if (b != 0) {
            super.write(b)
        }
    }

    @Throws(IOException::class)
    override fun write(raw: ByteArray, offset: Int, len: Int) {
        val filtered = ByteArray(len)
        var count = 0

        var i = 0
        while (i < len) {
            val b = raw[offset + i]
            if (b != 0.toByte()) {
                filtered[count] = b
                ++count
            }
            ++i
        }

        super.write(filtered, 0, count)
    }
}

/**
 * Wraps a process's output stream with a zero filter.
 */
private class ZeroFilteringProcess(private val process: Process) : Process() {
    private val output: OutputStream

    init {
        this.output = ZeroFilter(process.outputStream)
    }

    override fun getOutputStream() = output

    override fun getInputStream(): InputStream = process.inputStream

    override fun getErrorStream(): InputStream = process.errorStream

    @Throws(InterruptedException::class)
    override fun waitFor() = process.waitFor()

    override fun destroy() = process.destroy()

    override fun exitValue() = process.exitValue()
}

/**
 * Applies the ZeroFilter to this process.
 */
fun Process.zeroFiltered(): Process = ZeroFilteringProcess(this)
