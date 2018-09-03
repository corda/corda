package net.corda.webserver.api

/**
 * Extremely rudimentary query language which should most likely be replaced with a product.
 */
interface StatesQuery {
    companion object {
        fun select(criteria: Criteria): Selection {
            return Selection(criteria)
        }
    }

    // TODO make constructors private
    data class Selection(val criteria: Criteria) : StatesQuery

    interface Criteria {
        object AllDeals : Criteria

        data class Deal(val ref: String) : Criteria
    }
}
