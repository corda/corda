package net.corda.demobench.model

import net.corda.demobench.loggerFor
import org.h2.server.web.LocalWebServer
import org.h2.tools.Server
import org.h2.util.JdbcUtils
import java.util.concurrent.Executors
import kotlin.reflect.jvm.jvmName

class DBViewer : AutoCloseable {
    private val log = loggerFor<DBViewer>()

    private val webServer: Server
    private val pool = Executors.newCachedThreadPool()
    private val t = Thread("DBViewer")

    init {
        val ws = LocalWebServer()
        webServer = Server(ws, "-webPort", "0")
        ws.setShutdownHandler(webServer)

        webServer.setShutdownHandler {
            webServer.stop()
        }

        t.run {
            webServer.start()
        }
    }

    override fun close() {
        webServer.shutdown()
        pool.shutdown()
        t.join()
    }

    fun openBrowser(h2Port: Int) {
        val conn = JdbcUtils.getConnection(
            org.h2.Driver::class.jvmName,
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
