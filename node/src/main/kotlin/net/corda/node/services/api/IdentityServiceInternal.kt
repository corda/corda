/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.api

import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.IdentityService

interface IdentityServiceInternal : IdentityService {
    /** This method exists so it can be mocked with doNothing, rather than having to make up a possibly invalid return value. */
    fun justVerifyAndRegisterIdentity(identity: PartyAndCertificate) {
        verifyAndRegisterIdentity(identity)
    }
}
