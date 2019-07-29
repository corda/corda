package net.corda.node.internal

import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.ContractVerifier
import net.corda.core.internal.Verifier
import net.corda.core.transactions.LedgerTransaction
import net.corda.djvm.SandboxConfiguration
import net.corda.djvm.analysis.AnalysisConfiguration
import net.corda.djvm.execution.*
import net.corda.djvm.source.ClassSource

class DeterministicVerifier(
    ltx: LedgerTransaction,
    transactionClassLoader: ClassLoader,
    private val analysisConfiguration: AnalysisConfiguration
) : Verifier(ltx, transactionClassLoader) {

    override fun verifyContracts() {
        try {
            val configuration = SandboxConfiguration.of(
                enableTracing = false,
                analysisConfiguration = analysisConfiguration
            )
            val executor = SandboxRawExecutor(configuration)
            executor.run(ClassSource.fromClassName(ContractVerifier::class.java.name), ltx)
        } catch (e: Exception) {
            throw DeterministicVerificationException(ltx.id, e.message ?: "", e)
        }
    }

    override fun close() {
        analysisConfiguration.close()
    }
}

class DeterministicVerificationException(id: SecureHash, message: String, cause: Throwable)
    : TransactionVerificationException(id, message, cause)
