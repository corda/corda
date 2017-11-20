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
import org.junit.rules.TemporaryFolder
import kotlin.test.assertTrue

class HsmTest {

    @Rule
    @JvmField
    val hsmSimulator: HsmSimulator = HsmSimulator()
    val testParameters = Parameters(
            dataSourceProperties = mock(),
            device = "${hsmSimulator.port}@${hsmSimulator.host}",
            keySpecifier = 1,
            keyGroup = "*"
    )

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var inputReader: InputReader

    @Before
    fun setUp() {
        inputReader = mock()
        whenever(inputReader.readLine()).thenReturn(hsmSimulator.cryptoUserCredentials().username)
        whenever(inputReader.readPassword(any())).thenReturn(hsmSimulator.cryptoUserCredentials().password)
    }

    @Test
    fun `Authenticator executes the block once user is successfully authenticated`() {
        // given
        val authenticator = Authenticator(testParameters.createProvider(), inputReader = inputReader)
        var executed = false

        // when
        authenticator.connectAndAuthenticate({ provider, signers ->
            executed = true
        })

        // then
        assertTrue(executed)
    }
}