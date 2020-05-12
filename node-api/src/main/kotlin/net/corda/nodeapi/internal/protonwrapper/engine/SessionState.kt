package net.corda.nodeapi.internal.protonwrapper.engine

import org.apache.qpid.proton.engine.Session

/**
 * In addition to holding the `Session` also tracks the state of it.
 */
internal class SessionState {

    enum class Value {
        UNINITIALIZED,
        ACTIVE,
        CLOSED
    }

    private var _value: Value = Value.UNINITIALIZED

    private var _session: Session? = null

    val value: Value get() = _value

    val session: Session? get() = _session

    fun init(session: Session) {
        require(value == Value.UNINITIALIZED)
        _value = Value.ACTIVE
        _session = session
    }

    fun close() {
        _value = Value.CLOSED
        _session = null
    }
}