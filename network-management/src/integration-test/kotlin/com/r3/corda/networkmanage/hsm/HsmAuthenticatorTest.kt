/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.hsm

import com.r3.corda.networkmanage.common.HsmBaseTest
import com.r3.corda.networkmanage.hsm.authentication.Authenticator
import com.r3.corda.networkmanage.hsm.authentication.createProvider
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertTrue

class HsmAuthenticatorTest : HsmBaseTest() {

    @Test
    fun `Authenticator executes the block once user is successfully authenticated`() {
        // given
        val userInput = givenHsmUserAuthenticationInput()
        val hsmSigningServiceConfig = createHsmSigningServiceConfig(createDoormanCertificateConfig(), null)
        val doormanCertificateConfig = hsmSigningServiceConfig.doorman!!
        val authenticator = Authenticator(provider = createProvider(
                doormanCertificateConfig.keyGroup,
                hsmSigningServiceConfig.keySpecifier,
                hsmSigningServiceConfig.device), inputReader = userInput)
        val executed = AtomicBoolean(false)

        // when
        authenticator.connectAndAuthenticate { _, _ -> executed.set(true) }

        // then
        assertTrue(executed.get())
    }
}