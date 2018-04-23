/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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
