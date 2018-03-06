/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.demobench.web

import net.corda.core.utilities.contextLogger
import org.h2.Driver
import org.h2.server.web.LocalWebServer
import org.h2.tools.Server
import org.h2.util.JdbcUtils
import java.sql.SQLException
import java.util.concurrent.Executors
import kotlin.reflect.jvm.jvmName

class DBViewer : AutoCloseable {
    private companion object {
        private val log = contextLogger()
    }

    private val webServer: Server
    private val pool = Executors.newCachedThreadPool()

    init {
        val ws = LocalWebServer()
        webServer = Server(ws, "-webPort", "0")
        ws.setShutdownHandler(webServer)

        webServer.setShutdownHandler {
            webServer.stop()
        }

        pool.submit {
            webServer.start()
        }
    }

    override fun close() {
        log.info("Shutting down")
        pool.shutdown()
        webServer.shutdown()
    }

    @Throws(SQLException::class)
    fun openBrowser(h2Port: Int) {
        val conn = JdbcUtils.getConnection(
                Driver::class.jvmName,
                "jdbc:h2:tcp://localhost:$h2Port/node",
                "sa",
                ""
        )

        val url = (webServer.service as LocalWebServer).addSession(conn)
        log.info("Session: {}", url)

        pool.execute {
            Server.openBrowser(url)
        }
    }

}
