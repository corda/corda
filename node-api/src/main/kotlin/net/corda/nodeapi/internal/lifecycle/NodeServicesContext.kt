package net.corda.nodeapi.internal.lifecycle

import net.corda.core.CordaInternal
import net.corda.core.serialization.SerializeAsToken

/**
 * Defines a set of properties that will be available for services to perform useful activity with side effects.
 */
interface NodeServicesContext : NodeInitialContext {

    /**
     * Special services which upon serialisation will be represented in the stream by a special token. On the remote side
     * during deserialization token will be read and corresponding instance found and wired as necessary.
     */
    @CordaInternal
    val tokenizableServices: List<SerializeAsToken>
}