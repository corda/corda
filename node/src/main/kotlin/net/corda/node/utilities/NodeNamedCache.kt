package net.corda.node.utilities

import com.codahale.metrics.MetricRegistry
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.services.config.NodeConfiguration
import java.util.concurrent.TimeUnit

/**
 * Allow passing metrics and config to caching implementations.  This is needs to be distinct from [NamedCacheFactory]
 * to avoid deterministic serialization from seeing metrics and config on method signatures.
 */
interface BindableNamedCacheFactory : NamedCacheFactory, SerializeAsToken {
    /**
     * Build a new cache factory of the same type that incorporates metrics.
     */
    fun bindWithMetrics(metricRegistry: MetricRegistry): BindableNamedCacheFactory

    /**
     * Build a new cache factory of the same type that incorporates the associated configuration.
     */
    fun bindWithConfig(nodeConfiguration: NodeConfiguration): BindableNamedCacheFactory
}

open class DefaultNamedCacheFactory protected constructor(private val metricRegistry: MetricRegistry?, private val nodeConfiguration: NodeConfiguration?) : BindableNamedCacheFactory, SingletonSerializeAsToken() {
    constructor() : this(null, null)

    override fun bindWithMetrics(metricRegistry: MetricRegistry): BindableNamedCacheFactory = DefaultNamedCacheFactory(metricRegistry, this.nodeConfiguration)
    override fun bindWithConfig(nodeConfiguration: NodeConfiguration): BindableNamedCacheFactory = DefaultNamedCacheFactory(this.metricRegistry, nodeConfiguration)

    open protected fun <K, V> configuredForNamed(caffeine: Caffeine<K, V>, name: String): Caffeine<K, V> {
        return with(nodeConfiguration!!) {
            when {
                name.startsWith("RPCSecurityManagerShiroCache_") -> with(security?.authService?.options?.cache!!) { caffeine.maximumSize(maxEntries).expireAfterWrite(expireAfterSecs, TimeUnit.SECONDS) }
                name == "RPCServer_observableSubscription" -> caffeine
                name == "RpcClientProxyHandler_rpcObservable" -> caffeine
                name == "SerializationScheme_attachmentClassloader" -> caffeine
                name == "HibernateConfiguration_sessionFactories" -> caffeine.maximumSize(database.mappedSchemaCacheSize)
                name == "DBTransactionStorage_transactions" -> caffeine.maximumWeight(transactionCacheSizeBytes)
                name == "NodeAttachmentService_attachmentContent" -> caffeine.maximumWeight(attachmentContentCacheSizeBytes)
                name == "NodeAttachmentService_attachmentPresence" -> caffeine.maximumSize(attachmentCacheBound)
                name == "NodeAttachmentService_contractAttachmentVersions" -> caffeine.maximumSize(defaultCacheSize)
                name == "PersistentIdentityService_partyByKey" -> caffeine.maximumSize(defaultCacheSize)
                name == "PersistentIdentityService_partyByName" -> caffeine.maximumSize(defaultCacheSize)
                name == "PersistentNetworkMap_nodesByKey" -> caffeine.maximumSize(defaultCacheSize)
                name == "PersistentNetworkMap_idByLegalName" -> caffeine.maximumSize(defaultCacheSize)
                name == "PersistentKeyManagementService_keys" -> caffeine.maximumSize(defaultCacheSize)
                name == "FlowDrainingMode_nodeProperties" -> caffeine.maximumSize(defaultCacheSize)
                name == "ContractUpgradeService_upgrades" -> caffeine.maximumSize(defaultCacheSize)
                name == "PersistentUniquenessProvider_transactions" -> caffeine.maximumSize(defaultCacheSize)
                name == "P2PMessageDeduplicator_processedMessages" -> caffeine.maximumSize(defaultCacheSize)
                name == "DeduplicationChecker_watermark" -> caffeine
                name == "BFTNonValidatingNotaryService_transactions" -> caffeine.maximumSize(defaultCacheSize)
                name == "RaftUniquenessProvider_transactions" -> caffeine.maximumSize(defaultCacheSize)
                name == "BasicHSMKeyManagementService_keys" -> caffeine.maximumSize(defaultCacheSize)
                name == "NodeParametersStorage_networkParametersByHash" -> caffeine.maximumSize(defaultCacheSize)
                name == "BasicHSMKeyManagementService_keyToExternalId" -> caffeine.maximumSize(defaultCacheSize)
                else -> throw IllegalArgumentException("Unexpected cache name $name. Did you add a new cache?")
            }
        }
    }

    protected fun checkState(name: String) {
        checkCacheName(name)
        checkNotNull(metricRegistry)
        checkNotNull(nodeConfiguration)
    }

    override fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String): Cache<K, V> {
        checkState(name)
        return configuredForNamed(caffeine, name).build<K, V>()
    }

    override fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String, loader: CacheLoader<K, V>): LoadingCache<K, V> {
        checkState(name)
        return configuredForNamed(caffeine, name).build<K, V>(loader)
    }

    open protected val defaultCacheSize = 1024L
}