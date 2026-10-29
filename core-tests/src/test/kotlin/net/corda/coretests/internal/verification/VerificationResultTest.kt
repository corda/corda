package net.corda.coretests.internal.verification

import net.corda.core.internal.verification.ContractGeneration.CORDA_4_11
import net.corda.core.internal.verification.ContractGeneration.CORDA_4_12
import net.corda.core.internal.verification.VerificationResult.External
import net.corda.core.internal.verification.VerificationResult.InProcess
import net.corda.core.internal.verification.VerificationResult.InProcessAndExternal
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.Try
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.Test
import org.mockito.Mockito.mock

class VerificationResultTest {
    private val ltx = mock(LedgerTransaction::class.java)
    private val success = Try.Success(Unit)

    private class Failure1 : Exception()
    private class Failure2 : Exception()
    private class Failure3 : Exception()

    @Test(timeout = 300_000)
    fun `in-process only`() {
        assertThat(InProcess(Try.Success(ltx)).enforceSuccess()).isSameAs(ltx)
        assertThatExceptionOfType(Failure1::class.java).isThrownBy { InProcess(Try.Failure(Failure1())).enforceSuccess() }
    }

    @Test(timeout = 300_000)
    fun `all versions succeed`() {
        assertThat(InProcessAndExternal(Try.Success(ltx), mapOf(CORDA_4_11 to success, CORDA_4_12 to success)).enforceSuccess())
                .isSameAs(ltx)
        assertThat(External(mapOf(CORDA_4_11 to success, CORDA_4_12 to success)).enforceSuccess()).isNull()
    }

    @Test(timeout = 300_000)
    fun `all versions fail`() {
        val result = InProcessAndExternal(
                Try.Failure(Failure1()),
                mapOf(CORDA_4_11 to Try.Failure(Failure2()), CORDA_4_12 to Try.Failure(Failure3()))
        )
        assertThatExceptionOfType(Failure1::class.java)
                .isThrownBy { result.enforceSuccess() }
                .satisfies({ assertThat(it.suppressed).hasOnlyElementsOfTypes(Failure2::class.java, Failure3::class.java).hasSize(2) })
        // With no in-process result the newest legacy version is the primary one
        assertThatExceptionOfType(Failure3::class.java).isThrownBy {
            External(mapOf(CORDA_4_11 to Try.Failure(Failure2()), CORDA_4_12 to Try.Failure(Failure3()))).enforceSuccess()
        }
    }

    @Test(timeout = 300_000)
    fun `current version succeeds but a legacy version fails`() {
        val result = InProcessAndExternal(Try.Success(ltx), mapOf(CORDA_4_11 to success, CORDA_4_12 to Try.Failure(Failure2())))
        assertThatIllegalStateException()
                .isThrownBy { result.enforceSuccess() }
                .withMessageContaining("Current version of transaction verified, but legacy version failed to verify")
                .withMessageContaining(CORDA_4_12.name)
                .withCauseInstanceOf(Failure2::class.java)
    }

    @Test(timeout = 300_000)
    fun `current version fails but a legacy version succeeds`() {
        val result = InProcessAndExternal(Try.Failure(Failure1()), mapOf(CORDA_4_11 to Try.Failure(Failure2()), CORDA_4_12 to success))
        assertThatIllegalStateException()
                .isThrownBy { result.enforceSuccess() }
                .withMessageContaining("Current version of transaction failed to verify, but legacy version did verify")
                .withMessageContaining(CORDA_4_12.name)
                .withCauseInstanceOf(Failure1::class.java)
    }

    @Test(timeout = 300_000)
    fun `legacy versions disagree`() {
        assertThatIllegalStateException()
                .isThrownBy { External(mapOf(CORDA_4_11 to Try.Failure(Failure2()), CORDA_4_12 to success)).enforceSuccess() }
                .withMessageContaining("verified, but legacy version failed to verify")
                .withMessageContaining(CORDA_4_11.name)
    }
}
