package net.corda.vega.plugin.customserializers

import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.schemas.CashSchema

/**
 * This just references a random class from the finance Cordapp for testing purposes.
 */
@Suppress("UNUSED")
class UnusedFinanceSerializer : SerializationCustomSerializer<CashSchema, UnusedFinanceSerializer.Proxy> {
    class Proxy
    override fun toProxy(obj: CashSchema): Proxy =Proxy()
    override fun fromProxy(proxy: Proxy): CashSchema = CashSchema
}

class Unused
@Suppress("UNUSED")
class UnusedFinanceSerializer1 : SerializationCustomSerializer<Unused, UnusedFinanceSerializer1.Proxy> {
    init {
        // Just instantiate some finance class.
        Cash()
    }
    class Proxy
    override fun toProxy(obj: Unused): Proxy =Proxy()
    override fun fromProxy(proxy: Proxy): Unused = Unused()
}
