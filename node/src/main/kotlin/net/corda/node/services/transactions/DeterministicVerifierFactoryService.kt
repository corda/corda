package net.corda.node.services.transactions

import net.corda.core.internal.BasicVerifier
import net.corda.core.internal.Verifier
import net.corda.core.serialization.ConstructorForDeserialization
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.DeprecatedConstructorForDeserialization
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.LedgerTransaction
import net.corda.djvm.analysis.AnalysisConfiguration
import net.corda.djvm.analysis.Whitelist
import net.corda.djvm.source.ApiSource
import net.corda.djvm.source.UserPathSource
import net.corda.djvm.source.UserSource
import net.corda.node.internal.djvm.DeterministicVerifier
import java.net.URL
import java.net.URLClassLoader

class DeterministicVerifierFactoryService(
    private val bootstrapSource: ApiSource,
    private val cordaSource: UserSource?
) : SingletonSerializeAsToken(), AutoCloseable {

    fun specialise(ltx: LedgerTransaction, classLoader: ClassLoader): Verifier {
        return (classLoader as? URLClassLoader)?.run {
            DeterministicVerifier(ltx, classLoader, createSandbox(classLoader.urLs))
        } ?: BasicVerifier(ltx, classLoader)
    }

    private fun createSandbox(userSource: Array<URL>): AnalysisConfiguration {
        return AnalysisConfiguration.createRoot(
            userSource = cordaSource!!,
            whitelist = Whitelist.MINIMAL,
            visibleAnnotations = setOf(
                CordaSerializable::class.java,
                ConstructorForDeserialization::class.java,
                DeprecatedConstructorForDeserialization::class.java
            ),
            bootstrapSource = bootstrapSource
        ).createChild(
            userSource = UserPathSource(userSource),
            newMinimumSeverityLevel = null,
            visibleAnnotations = emptySet()
        )
    }

    override fun close() {
        bootstrapSource.use {
            cordaSource?.close()
        }
    }
}
