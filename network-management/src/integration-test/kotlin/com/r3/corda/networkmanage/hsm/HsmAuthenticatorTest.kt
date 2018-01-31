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
        val authenticator = Authenticator(provider = hsmSigningServiceConfig.createProvider(hsmSigningServiceConfig.rootKeyGroup), inputReader = userInput)
        val executed = AtomicBoolean(false)

        // when
        authenticator.connectAndAuthenticate { _, _, _ -> executed.set(true) }

        // then
        assertTrue(executed.get())
    }
}