package org.h2.server.web

import java.sql.Connection

/**
 *
 */
class LocalWebServer : WebServer() {

    /**
     * Create a new session that will not kill the entire
     * web server if/when we disconnect it.
     */
    override fun addSession(conn: Connection): String {
        val session = createNewSession("local")
        session.setConnection(conn)
        session.put("url", conn.metaData.url)
        val s = session.get("sessionId") as String
        return url + "/frame.jsp?jsessionid=" + s
    }

}
