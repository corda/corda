/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.internal.artemis

import net.corda.node.internal.Connectable
import net.corda.node.internal.LifecycleSupport
import org.apache.activemq.artemis.api.core.client.ClientConsumer
import org.apache.activemq.artemis.api.core.client.ClientMessage
import org.apache.activemq.artemis.api.core.client.ClientSession
import rx.Observable
import rx.subjects.PublishSubject

interface ReactiveArtemisConsumer : LifecycleSupport, Connectable {

    val messages: Observable<ClientMessage>

    companion object {

        fun multiplex(createSession: () -> ClientSession, queueName: String, filter: String? = null, vararg queueNames: String): ReactiveArtemisConsumer {

            return MultiplexingReactiveArtemisConsumer(setOf(queueName, *queueNames), createSession, filter)
        }

        fun multiplex(queueNames: Set<String>, createSession: () -> ClientSession, filter: String? = null): ReactiveArtemisConsumer {

            return MultiplexingReactiveArtemisConsumer(queueNames, createSession, filter)
        }
    }
}

private class MultiplexingReactiveArtemisConsumer(private val queueNames: Set<String>, private val createSession: () -> ClientSession, private val filter: String?) : ReactiveArtemisConsumer {

    private var startedFlag = false
    override var connected = false

    override val messages: PublishSubject<ClientMessage> = PublishSubject.create<ClientMessage>()

    private val consumers = mutableSetOf<ClientConsumer>()
    private val sessions = mutableSetOf<ClientSession>()

    override fun start() {

        synchronized(this) {
            require(!startedFlag)
            connect()
            startedFlag = true
        }
    }

    override fun stop() {

        synchronized(this) {
            if (startedFlag) {
                disconnect()
                startedFlag = false
            }
            messages.onCompleted()
        }
    }

    override fun connect() {

        synchronized(this) {
            require(!connected)
            queueNames.forEach { queue ->
                createSession().apply {
                    start()
                    consumers += filter?.let { createConsumer(queue, it) } ?: createConsumer(queue)
                    sessions += this
                }
            }
            consumers.forEach { consumer ->
                consumer.setMessageHandler { message ->
                    messages.onNext(message)
                }
            }
            connected = true
        }
    }

    override fun disconnect() {

        synchronized(this) {
            if (connected) {
                consumers.forEach(ClientConsumer::close)
                sessions.forEach(ClientSession::close)
                consumers.clear()
                sessions.clear()
                connected = false
            }
        }
    }

    override val started: Boolean
        get() = startedFlag
}