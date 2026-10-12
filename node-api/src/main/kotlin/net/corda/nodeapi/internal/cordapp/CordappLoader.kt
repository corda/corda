package net.corda.nodeapi.internal.cordapp

import net.corda.core.cordapp.Cordapp
import net.corda.core.internal.cordapp.CordappImpl
import net.corda.core.internal.flatMapToSet
import net.corda.core.schemas.MappedSchema

/**
 * Handles loading [Cordapp]s.
 */
interface CordappLoader : AutoCloseable {
    /**
     * Returns all [Cordapp]s found.
     */
    val cordapps: List<CordappImpl>

    /**
     * Returns all legacy (4.11 or older) contract CorDapps. These are used to form backward compatible transactions.
     */
    val legacyContractCordapps: List<CordappImpl>

    /**
     * Returns a [ClassLoader] containing all types from all [Cordapp]s.
     */
    val appClassLoader: ClassLoader
}

/**
 * Returns all [MappedSchema] found inside the [Cordapp]s.
 */
val CordappLoader.cordappSchemas: Set<MappedSchema>
    get() = cordapps.flatMapToSet { it.customSchemas }
