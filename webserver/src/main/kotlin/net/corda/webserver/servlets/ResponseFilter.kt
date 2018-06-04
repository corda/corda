package net.corda.webserver.servlets

import java.io.IOException
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerResponseContext
import javax.ws.rs.container.ContainerResponseFilter
import javax.ws.rs.ext.Provider

/**
 * This adds headers needed for cross site scripting on API clients.
 */
@Provider
class ResponseFilter : ContainerResponseFilter {
    @Throws(IOException::class)
    override fun filter(requestContext: ContainerRequestContext, responseContext: ContainerResponseContext) {
        val headers = responseContext.headers

        /**
         * TODO we need to revisit this for security reasons
         *
         * We don't want this scriptable from any web page anywhere, but for demo reasons
         * we're making this really easy to access pending a proper security approach including
         * access control and authentication at a network and software level.
         *
         */
        headers.add("Access-Control-Allow-Origin", "*")

        if (requestContext.method == "OPTIONS") {
            headers.add("Access-Control-Allow-Headers", "Content-Type,Accept,Origin")
            headers.add("Access-Control-Allow-Methods", "POST,PUT,GET,OPTIONS")
        }
    }
}
