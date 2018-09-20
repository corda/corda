package net.corda.tools.error.codes.server.commons.lifecycle

interface WithLifeCycle : Startable, Stoppable, AutoCloseable {

    override fun stop() = close()
}