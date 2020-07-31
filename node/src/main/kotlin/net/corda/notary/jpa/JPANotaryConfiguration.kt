package net.corda.notary.jpa

data class JPANotaryConfiguration(
    val batchSize: Int = 32,
    val batchTimeoutMs: Long = 200L,
    val maxInputStates: Int = 2000,
    val maxDBTransactionRetryCount: Int = 10,
    val backOffBaseMs: Long = 20L
)
