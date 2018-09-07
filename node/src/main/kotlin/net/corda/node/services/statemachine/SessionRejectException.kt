package net.corda.node.services.statemachine

import net.corda.core.CordaException

/**
 * An exception propagated and thrown in case a session initiation fails.
 */
class SessionRejectException(val reason: String) : CordaException(reason)
