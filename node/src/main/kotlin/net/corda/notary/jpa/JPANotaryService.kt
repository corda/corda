package net.corda.notary.jpa

import net.corda.core.crypto.SecureHash
import net.corda.node.services.api.ServiceHubInternal
import net.corda.nodeapi.internal.config.parseAs
import net.corda.notary.common.ValidationModeNotaryService
import net.corda.notary.common.signBatch
import java.security.PublicKey

/** Notary service backed by a relational database. */
class JPANotaryService(services: ServiceHubInternal, notaryIdentityKey: PublicKey) : ValidationModeNotaryService(services, notaryIdentityKey) {

    override val uniquenessProvider = with(services) {
        val jpaNotaryConfig = try {
            notaryConfig.extraConfig?.parseAs() ?: JPANotaryConfiguration()
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to register ${JPANotaryService::class.java}: extra notary configuration parameters invalid", e)
        }
        JPAUniquenessProvider(
                clock,
                database,
                jpaNotaryConfig,
                configuration.myLegalName,
                ::signTransactionBatch
        )
    }

    private fun signTransactionBatch(txIds: Iterable<SecureHash>)
            = signBatch(txIds, notaryIdentityKey, services)

    override fun start() {
    }

    override fun stop() {
        uniquenessProvider.stop()
    }
}
