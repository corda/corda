package net.corda.nodeapi.internal.zookeeper


/**
 * Listener interface for leader election results, to avoid public reference to shadowed curator classes.
 */
interface CordaLeaderListener {
    /**
     * This is called when the LeaderLatch's state goes from hasLeadership = false to hasLeadership = true.
     *
     * Note that it is possible that by the time this method call happens, hasLeadership has fallen back to false.  If
     * this occurs, you can expect {@link #notLeader()} to also be called.
     */
    fun notLeader()

    /**
     * This is called when the LeaderLatch's state goes from hasLeadership = true to hasLeadership = false.
     *
     * Note that it is possible that by the time this method call happens, hasLeadership has become true.  If
     * this occurs, you can expect {@link #isLeader()} to also be called.
     */
    fun isLeader()
}

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
     * @param listener an instance of [CordaLeaderListener] that will be used for election notifications
     */
    fun addLeadershipListener(listener: CordaLeaderListener)

    /**
     * @param listener the [CordaLeaderListener] instance to be removed
     */
    fun removeLeadershipListener(listener: CordaLeaderListener)

    /**
     * @return [true] if client is the current leader, [false] otherwise
     */
    fun isLeader(): Boolean

    /**
     * @return [true] if client is started, [false] otherwise
     */
    fun isStarted(): Boolean
}