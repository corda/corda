package net.corda.contracts

import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import java.util.*

/**
 * Created by N992551 on 30.06.2016.
 */

object LocDataStructures {
    enum class WeightUnit {
        KG,
        LBS
    }

    data class Weight(
            val quantity: Double,
            val unit: LocDataStructures.WeightUnit
    )

    data class Company(
            val name: String,
            val address: String,
            val phone: String?
    )

    data class Person(
            val name: String,
            val address: String,
            val phone: String?
    )

    data class Port(
            val country: String,
            val city: String,
            val address: String?,
            val name: String?,
            val state: String?
    )

    data class Location(
            val country: String,
            val state: String?,
            val city: String
    )

    data class Good(
            val description: String,
            val quantity: Int,
            val grossWeight: LocDataStructures.Weight?
    ) {
        init {
            require(quantity > 0) { "The good quantity must be a positive value." }
        }
    }

    data class PricedGood(
            val description: String,
            val purchaseOrderRef: String?,
            val quantity: Int,
            val unitPrice: Amount<Issued<Currency>>,
            val grossWeight: LocDataStructures.Weight?
    ) {
        init {
            require(quantity > 0) { "The good quantity must be a positive value." }
        }

        fun totalPrice(): Amount<Issued<Currency>> {
            return unitPrice.times(quantity)
        }
    }

    enum class CreditType {
        //TODO: There are a lot of types
        SIGHT,
        DEFERRED_PAYMENT,
        ACCEPTANCE,
        NEGOTIABLE_CREDIT,
        TRANSFERABLE,
        STANDBY,
        REVOLVING,
        RED_CLAUSE,
        GREEN_CLAUSE
    }

}
