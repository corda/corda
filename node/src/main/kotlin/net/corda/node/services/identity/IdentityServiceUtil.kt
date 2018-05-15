/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */
package net.corda.node.services.identity

import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party


fun partiesFromName(query: String, exactMatch: Boolean, x500name: CordaX500Name, results: LinkedHashSet<Party>, party: Party) {

    val components = listOfNotNull(x500name.commonName, x500name.organisationUnit, x500name.organisation, x500name.locality, x500name.state, x500name.country)
    components.forEach { component ->
        if (exactMatch && component == query) {
            results += party
        } else if (!exactMatch) {
            // We can imagine this being a query over a lucene index in future.
            //
            // Kostas says: We can easily use the Jaro-Winkler distance metric as it is best suited for short
            // strings such as entity/company names, and to detect small typos. We can also apply it for city
            // or any keyword related search in lists of records (not raw text - for raw text we need indexing)
            // and we can return results in hierarchical order (based on normalised String similarity 0.0-1.0).
            if (component.contains(query, ignoreCase = true))
                results += party
        }
    }
}