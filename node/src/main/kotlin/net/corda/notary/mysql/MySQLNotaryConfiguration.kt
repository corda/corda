package net.corda.notary.mysql

import java.util.*

data class MySQLNotaryConfiguration(
        val dataSource: Properties,
        /**
         * Number of times to attempt to reconnect to the database.
         */
        val connectionRetries: Int = 2, // Default value for a 3 server cluster.
        /**
         * Time increment between re-connection attempts.
         *
         * The total back-off duration is calculated as: backOffIncrement * backOffBase ^ currentRetryCount
         */
        val backOffIncrement: Int = 500,
        /** Exponential back-off multiplier base. */
        val backOffBase: Double = 1.5,
        /** The maximum number of transactions processed in a single batch. */
        val maxBatchSize: Int = 500,
        /** The maximum combined number of input states processed in a single batch. */
        val maxBatchInputStates: Int = 10_000,
        /** A batch will be processed after a specified timeout even if it has not yet reached full capacity. */
        val batchTimeoutMs: Long = 200,
        /**
         * The maximum number of commit requests in flight. Once the capacity is reached the service will block on
         * further commit requests.
         */
        val maxQueueSize: Int = 100_000
) {
    init {
        require(connectionRetries >= 0) { "connectionRetries cannot be negative" }
    }
}
