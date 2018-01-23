package net.corda.node.internal

import rx.Observable

interface LifecycleSupport : Startable, Stoppable

interface Stoppable {
    fun stop(): Observable<Unit>
}

interface Startable {
    fun start(): Observable<Unit>

    val started: Boolean
}

fun LifecycleSupport.startBlocking() = start().toBlocking().subscribe()

fun LifecycleSupport.stopBlocking() = stop().toBlocking().subscribe()