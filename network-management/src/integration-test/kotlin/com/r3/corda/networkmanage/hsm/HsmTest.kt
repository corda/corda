package com.r3.corda.networkmanage.hsm

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import com.r3.corda.networkmanage.HsmSimulator
import com.r3.corda.networkmanage.hsm.authentication.Authenticator
import com.r3.corda.networkmanage.hsm.authentication.createProvider
import com.r3.corda.networkmanage.hsm.configuration.Parameters
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.Console
import kotlin.test.assertTrue

class HsmTest {

    @Rule
    @JvmField
    val hsmSimulator: HsmSimulator = HsmSimulator()

    private var console: Console? = null

    @Before
    fun setUp() {
        console = mock()
    }

    @Test
    fun `Authenticator executes the block once user is successfully authenticated`() {
        // given
        val parameters = Parameters(
                dataSourceProperties = mock(),
                device = "${hsmSimulator.port}@${hsmSimulator.host}",
                keySpecifier = 1,
                keyGroup = "*"
        )
        whenever(console?.readLine()).thenReturn(hsmSimulator.cryptoUserCredentials().username)
        whenever(console?.readPassword(any())).thenReturn(hsmSimulator.cryptoUserCredentials().password.toCharArray())
        val authenticator = Authenticator(parameters.createProvider(), console = console)
        var executed = false

        // when
        authenticator.connectAndAuthenticate({ provider, signers ->
            executed = true
        })

        // then
        assertTrue(executed)
    }


}