package net.corda.bridge.services

import net.corda.bridge.services.api.FirewallAuditService
import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import rx.Observable
import rx.subjects.PublishSubject
import java.net.InetSocketAddress

class TestAuditService() : FirewallAuditService, TestServiceBase() {
    enum class AuditEvent {
        SUCCESSFUL_CONNECTION,
        FAILED_CONNECTION,
        PACKET_DROP,
        PACKET_ACCEPT,
        STATUS_CHANGE
    }

    var eventCount: Int = 0
        private set

    private val _onAuditEvent = PublishSubject.create<AuditEvent>().toSerialized()
    val onAuditEvent: Observable<AuditEvent>
        get() = _onAuditEvent

    override fun successfulConnectionEvent(inbound: Boolean, sourceIP: InetSocketAddress, certificateSubject: String, msg: String) {
        ++eventCount
        _onAuditEvent.onNext(AuditEvent.SUCCESSFUL_CONNECTION)
    }

    override fun failedConnectionEvent(inbound: Boolean, sourceIP: InetSocketAddress?, certificateSubject: String?, msg: String) {
        ++eventCount
        _onAuditEvent.onNext(AuditEvent.FAILED_CONNECTION)
    }

    override fun packetDropEvent(packet: ReceivedMessage?, msg: String) {
        ++eventCount
        _onAuditEvent.onNext(AuditEvent.PACKET_DROP)
    }

    override fun packetAcceptedEvent(packet: ReceivedMessage) {
        ++eventCount
        _onAuditEvent.onNext(AuditEvent.PACKET_ACCEPT)
    }

    override fun statusChangeEvent(msg: String) {
        ++eventCount
        _onAuditEvent.onNext(AuditEvent.STATUS_CHANGE)
    }
}