package api

/**
 * Extremely rudimentary query language which should most likely be replaced with a product
 */
interface StatesQuery {
    companion object {
        fun select(criteria: Criteria): Selection {
            return Selection(criteria)
        }

        fun selectAllDeals(): Selection {
            return select(Criteria.AllDeals)
        }

        fun selectDeal(ref: String): Selection {
            return select(Criteria.Deal(ref))
        }

    }

    // TODO make constructors private
    data class Selection(val criteria: Criteria) : StatesQuery

    interface Criteria {
        object AllDeals : Criteria

        data class Deal(val ref: String) : Criteria
    }
}
