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

import net.corda.behave.logging.getLogger
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ChannelShell
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.channel.SttySupport
import org.crsh.util.Utils
import java.io.*
import java.time.Duration
import java.util.concurrent.TimeUnit

open class SSHClient private constructor(
        private val client: SshClient,
        private val outputStream: OutputStream,
        private val inputStream: InputStream,
        private val session: ClientSession,
        private val channel: ChannelShell
) : Closeable {

    private var isClosed = false

    fun read(): Int? {
        if (isClosed) {
            return null
        }
        val char = inputStream.read()
        return if (char != -1) {
            char
        } else {
            null
        }
    }

    fun readLine(): String? {
        if (isClosed) {
            return null
        }
        var ch: Int?
        val lineBuffer = mutableListOf<Char>()
        while (true) {
            ch = read()
            if (ch == null) {
                if (lineBuffer.isEmpty()) {
                    return null
                }
                break
            }
            lineBuffer.add(ch.toChar())
            if (ch == 10) {
                break
            }
        }
        return String(lineBuffer.toCharArray())
    }

    fun write(s: CharSequence) {
        if (isClosed) {
            return
        }
        write(*s.toString().toByteArray(UTF8))
    }

    fun write(vararg bytes: Byte) {
        if (isClosed) {
            return
        }
        outputStream.write(bytes)
    }

    fun writeLine(s: String) {
        write("$s\n")
        flush()
    }

    fun flush() {
        if (isClosed) {
            return
        }
        outputStream.flush()
    }

    override fun close() {
        if (isClosed) {
            return
        }
        try {
            Utils.close(outputStream)
            channel.close(false)
            session.close(false)
            client.stop()
        } finally {
            isClosed = true
        }
    }

    companion object {

        private val log = getLogger<SSHClient>()

        fun connect(
                port: Int,
                password: String,
                hostname: String = "localhost",
                username: String = "corda",
                timeout: Duration = Duration.ofSeconds(4)
        ): SSHClient {
            val tty = SttySupport.parsePtyModes(TTY)
            val client = SshClient.setUpDefaultClient()
            client.start()

            log.info("Connecting to $hostname:$port ...")
            val session = client
                    .connect(username, hostname, port)
                    .verify(timeout.seconds, TimeUnit.SECONDS)
                    .session

            log.info("Authenticating using password identity ...")
            session.addPasswordIdentity(password)
            val authFuture = session.auth().verify(timeout.seconds, TimeUnit.SECONDS)

            authFuture.addListener {
                log.info("Authentication completed with " + if (it.isSuccess) "success" else "failure")
            }

            val channel = session.createShellChannel()
            channel.ptyModes = tty

            val outputStream = PipedOutputStream()
            val channelIn = PipedInputStream(outputStream)

            val channelOut = PipedOutputStream()
            val inputStream = PipedInputStream(channelOut)

            channel.`in` = channelIn
            channel.out = channelOut
            channel.err = ByteArrayOutputStream()
            channel.open()

            return SSHClient(client, outputStream, inputStream, session, channel)
        }

        private const val TTY = "speed 9600 baud; 36 rows; 180 columns;\n" +
                "lflags: icanon isig iexten echo echoe -echok echoke -echonl echoctl\n" +
                "\t-echoprt -altwerase -noflsh -tostop -flusho pendin -nokerninfo\n" +
                "\t-extproc\n" +
                "iflags: -istrip icrnl -inlcr -igncr ixon -ixoff ixany imaxbel iutf8\n" +
                "\t-ignbrk brkint -inpck -ignpar -parmrk\n" +
                "oflags: opost onlcr -oxtabs -onocr -onlret\n" +
                "cflags: cread cs8 -parenb -parodd hupcl -clocal -cstopb -crtscts -dsrflow\n" +
                "\t-dtrflow -mdmbuf\n" +
                "cchars: discard = ^O; dsusp = ^Y; eof = ^D; eol = <undef>;\n" +
                "\teol2 = <undef>; erase = ^?; intr = ^C; kill = ^U; lnext = ^V;\n" +
                "\tmin = 1; quit = ^\\; reprint = ^R; start = ^Q; status = ^T;\n" +
                "\tstop = ^S; susp = ^Z; time = 0; werase = ^W;"

        private val UTF8 = charset("UTF-8")

    }

}