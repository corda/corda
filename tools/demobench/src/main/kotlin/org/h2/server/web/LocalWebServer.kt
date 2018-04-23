/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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
        return url + "/frame.jsp?jsessionid=" + s
    }

}
