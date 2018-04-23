/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.transactions

import net.corda.core.flows.FlowSession
import net.corda.core.flows.NotaryFlow
import net.corda.core.node.services.TrustedAuthorityNotaryService
import net.corda.node.services.api.ServiceHubInternal
import java.security.PublicKey

/** A Notary service that validates the transaction chain of the submitted transaction before committing it */
class ValidatingNotaryService(override val services: ServiceHubInternal, override val notaryIdentityKey: PublicKey) : TrustedAuthorityNotaryService() {
    override val uniquenessProvider = PersistentUniquenessProvider(services.clock)

    override fun createServiceFlow(otherPartySession: FlowSession): NotaryFlow.Service = ValidatingNotaryFlow(otherPartySession, this)

    override fun start() {}
    override fun stop() {}
}