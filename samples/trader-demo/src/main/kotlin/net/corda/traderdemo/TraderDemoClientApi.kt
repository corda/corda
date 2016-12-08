package net.corda.traderdemo

import com.google.common.net.HostAndPort
import net.corda.core.contracts.Amount
import net.corda.core.contracts.DOLLARS
import net.corda.testing.http.HttpApi
import java.util.*

/**
 * Interface for communicating with nodes running the trader demo.
 */
class TraderDemoClientApi(hostAndPort: HostAndPort) {
    private val api = HttpApi.fromHostAndPort(hostAndPort, apiRoot)

    fun runBuyer(amount: Amount<Currency> = 30000.0.DOLLARS, notary: String = "Notary"): Boolean {
        return api.putJson("create-test-cash", mapOf("amount" to amount.quantity, "notary" to notary))
    }

    fun runSeller(amount: Amount<Currency> = 1000.0.DOLLARS, counterparty: String): Boolean {
        return api.postJson("$counterparty/sell-cash", mapOf("amount" to amount.quantity))
    }

    private companion object {
        private val apiRoot = "api/traderdemo"
    }
}
