package net.corda.core.internal

object NodeProperties {
    // TODO: Use [NetworkParameters] once it's available in corda core.
    private var _maxTransactionSize: Int? = null

    var maxTransactionSize: Int
        get() = _maxTransactionSize ?: throw IllegalArgumentException("Property 'maxTransactionSize' has not been initialised.")
        set(value) {
            _maxTransactionSize = value
        }
}