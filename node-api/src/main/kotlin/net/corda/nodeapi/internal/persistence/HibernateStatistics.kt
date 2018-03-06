/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.persistence

import javax.management.MXBean

import org.hibernate.stat.Statistics
import org.hibernate.stat.SecondLevelCacheStatistics
import org.hibernate.stat.QueryStatistics
import org.hibernate.stat.NaturalIdCacheStatistics
import org.hibernate.stat.EntityStatistics
import org.hibernate.stat.CollectionStatistics

/**
 * Exposes Hibernate [Statistics] contract as JMX resource.
 */
@MXBean
interface StatisticsService : Statistics

/**
 * Implements the MXBean interface by delegating through the actual [Statistics] implementation retrieved from the
 * session factory.
 */
class DelegatingStatisticsService(private val delegate: Statistics) : StatisticsService {

    override fun clear() {
        delegate.clear()
    }

    override fun getCloseStatementCount(): Long {
        return delegate.closeStatementCount
    }

    override fun getCollectionFetchCount(): Long {
        return delegate.collectionFetchCount
    }

    override fun getCollectionLoadCount(): Long {
        return delegate.collectionLoadCount
    }

    override fun getCollectionRecreateCount(): Long {
        return delegate.collectionRecreateCount
    }

    override fun getCollectionRemoveCount(): Long {
        return delegate.collectionRemoveCount
    }

    override fun getCollectionRoleNames(): Array<String> {
        return delegate.collectionRoleNames
    }

    override fun getCollectionStatistics(arg0: String): CollectionStatistics {
        return delegate.getCollectionStatistics(arg0)
    }

    override fun getCollectionUpdateCount(): Long {
        return delegate.collectionUpdateCount
    }

    override fun getConnectCount(): Long {
        return delegate.connectCount
    }

    override fun getEntityDeleteCount(): Long {
        return delegate.entityDeleteCount
    }

    override fun getEntityFetchCount(): Long {
        return delegate.entityFetchCount
    }

    override fun getEntityInsertCount(): Long {
        return delegate.entityInsertCount
    }

    override fun getEntityLoadCount(): Long {
        return delegate.entityLoadCount
    }

    override fun getEntityNames(): Array<String> {
        return delegate.entityNames
    }

    override fun getEntityStatistics(arg0: String): EntityStatistics {
        return delegate.getEntityStatistics(arg0)
    }

    override fun getEntityUpdateCount(): Long {
        return delegate.entityUpdateCount
    }

    override fun getFlushCount(): Long {
        return delegate.flushCount
    }

    override fun getNaturalIdCacheHitCount(): Long {
        return delegate.naturalIdCacheHitCount
    }

    override fun getNaturalIdCacheMissCount(): Long {
        return delegate.naturalIdCacheMissCount
    }

    override fun getNaturalIdCachePutCount(): Long {
        return delegate.naturalIdCachePutCount
    }

    override fun getNaturalIdCacheStatistics(arg0: String): NaturalIdCacheStatistics {
        return delegate.getNaturalIdCacheStatistics(arg0)
    }

    override fun getNaturalIdQueryExecutionCount(): Long {
        return delegate.naturalIdQueryExecutionCount
    }

    override fun getNaturalIdQueryExecutionMaxTime(): Long {
        return delegate.naturalIdQueryExecutionMaxTime
    }

    override fun getNaturalIdQueryExecutionMaxTimeRegion(): String {
        return delegate.naturalIdQueryExecutionMaxTimeRegion
    }

    override fun getOptimisticFailureCount(): Long {
        return delegate.optimisticFailureCount
    }

    override fun getPrepareStatementCount(): Long {
        return delegate.prepareStatementCount
    }

    override fun getQueries(): Array<String> {
        return delegate.queries
    }

    override fun getQueryCacheHitCount(): Long {
        return delegate.queryCacheHitCount
    }

    override fun getQueryCacheMissCount(): Long {
        return delegate.queryCacheMissCount
    }

    override fun getQueryCachePutCount(): Long {
        return delegate.queryCachePutCount
    }

    override fun getQueryExecutionCount(): Long {
        return delegate.queryExecutionCount
    }

    override fun getQueryExecutionMaxTime(): Long {
        return delegate.queryExecutionMaxTime
    }

    override fun getQueryExecutionMaxTimeQueryString(): String {
        return delegate.queryExecutionMaxTimeQueryString
    }

    override fun getQueryStatistics(arg0: String): QueryStatistics {
        return delegate.getQueryStatistics(arg0)
    }

    override fun getSecondLevelCacheHitCount(): Long {
        return delegate.secondLevelCacheHitCount
    }

    override fun getSecondLevelCacheMissCount(): Long {
        return delegate.secondLevelCacheMissCount
    }

    override fun getSecondLevelCachePutCount(): Long {
        return delegate.secondLevelCachePutCount
    }

    override fun getSecondLevelCacheRegionNames(): Array<String> {
        return delegate.secondLevelCacheRegionNames
    }

    override fun getSecondLevelCacheStatistics(arg0: String): SecondLevelCacheStatistics {
        return delegate.getSecondLevelCacheStatistics(arg0)
    }

    override fun getSessionCloseCount(): Long {
        return delegate.sessionCloseCount
    }

    override fun getSessionOpenCount(): Long {
        return delegate.sessionOpenCount
    }

    override fun getStartTime(): Long {
        return delegate.startTime
    }

    override fun getSuccessfulTransactionCount(): Long {
        return delegate.successfulTransactionCount
    }

    override fun getTransactionCount(): Long {
        return delegate.transactionCount
    }

    override fun getUpdateTimestampsCacheHitCount(): Long {
        return delegate.updateTimestampsCacheHitCount
    }

    override fun getUpdateTimestampsCacheMissCount(): Long {
        return delegate.updateTimestampsCacheMissCount
    }

    override fun getUpdateTimestampsCachePutCount(): Long {
        return delegate.updateTimestampsCachePutCount
    }

    override fun isStatisticsEnabled(): Boolean {
        return delegate.isStatisticsEnabled
    }

    override fun logSummary() {
        delegate.logSummary()
    }

    override fun setStatisticsEnabled(arg0: Boolean) {
        delegate.isStatisticsEnabled = arg0
    }
}