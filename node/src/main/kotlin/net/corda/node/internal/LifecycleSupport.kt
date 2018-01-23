package net.corda.node.internal

interface LifecycleSupport : Startable, Stoppable

interface Stoppable {
    fun stop()
}

interface Startable {
    fun start()

    val started: Boolean
}