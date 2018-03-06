/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.zookeeper

import org.apache.curator.framework.recipes.leader.LeaderLatchListener

interface ZkLeader {
    /**
     * Starts the client and connects to the Zookeeper server.
     */
    fun start()

    /**
     * Closes the connection to the Zookeeper server. If the client is involved in the election process, it will drop out
     * and relinquish leadership.
     */
    fun close()

    /**
     * Joins election process. Subsequent calls will have no effect if client is leader or a candidate.
     * Throws [IllegalStateException] if the client isn't started.
     */
    @Throws(IllegalStateException::class)
    fun requestLeadership()

    /**
     * Withdraws client from the election process if it is the leader. A new election will be triggered for remaining
     * candidates. If the client isn't the leader, nothing will happen.
     */
    fun relinquishLeadership()

    /**
     * @param listener an instance of [LeaderLatchListener] that will be used for election notifications
     */
    fun addLeadershipListener(listener: LeaderLatchListener)

    /**
     * @param listener the [LeaderLatchListener] instance to be removed
     */
    fun removeLeaderShipListener(listener: LeaderLatchListener)

    /**
     * @return [true] if client is the current leader, [false] otherwise
     */
    fun isLeader(): Boolean

    /**
     * @return [true] if client is started, [false] otherwise
     */
    fun isStarted(): Boolean
}