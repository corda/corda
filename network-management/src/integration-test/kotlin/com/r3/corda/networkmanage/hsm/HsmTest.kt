package com.r3.corda.networkmanage.hsm

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import com.r3.corda.networkmanage.HsmSimulator
import com.r3.corda.networkmanage.hsm.authentication.Authenticator
import com.r3.corda.networkmanage.hsm.authentication.InputReader
import com.r3.corda.networkmanage.hsm.authentication.createProvider
import com.r3.corda.networkmanage.hsm.configuration.Parameters
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class HsmTest {

    @Rule
    @JvmField
    val hsmSimulator: HsmSimulator = HsmSimulator()

    private lateinit var inputReader: InputReader

    @Before
    fun setUp() {
        inputReader = mock()
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
        whenever(inputReader.readLine()).thenReturn(hsmSimulator.cryptoUserCredentials().username)
        whenever(inputReader.readPassword(any())).thenReturn(hsmSimulator.cryptoUserCredentials().password)
        val authenticator = Authenticator(parameters.createProvider(), inputReader = inputReader)
        var executed = false

        // when
        authenticator.connectAndAuthenticate({ provider, signers ->
            executed = true
        })

        // then
        assertTrue(executed)
    }


}