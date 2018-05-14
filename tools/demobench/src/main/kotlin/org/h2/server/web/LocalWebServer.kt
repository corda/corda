package org.h2.server.web

import java.sql.Connection
import java.sql.SQLException

class LocalWebServer : WebServer() {

    /**
     * Create a new session that will not kill the entire
     * web server if/when we disconnect it.
     */
    @Throws(SQLException::class)
    override fun addSession(conn: Connection): String {
        val session = createNewSession("local")
        session.connection = conn
        session.put("url", conn.metaData.url)
        val s = session.get("sessionId") as String
        return "$url/frame.jsp?jsessionid=$s"
    }
}
