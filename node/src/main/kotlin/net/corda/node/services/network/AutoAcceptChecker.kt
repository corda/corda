package net.corda.node.services.network

import net.corda.core.node.NetworkParameters

/**
 * Compares old and new parameters and returns true if the only differences are for auto acceptable parameters
 */
fun autoAcceptParameters(oldNetParams: NetworkParameters, newNetParams: NetworkParameters): Boolean {
    // should return true if the only change is additions to the whitelistedContract or packageOwnershipMap
    // if completely new key in whitelist map then can just add them
    // if adding to list of current key then need to check that its just additions
    // therefore it is not enough to just check size
    if (newNetParams == oldNetParams.copy(
                    epoch = newNetParams.epoch,
                    modifiedTime = newNetParams.modifiedTime,
                    whitelistedContractImplementations = newNetParams.whitelistedContractImplementations,
                    packageOwnership = newNetParams.packageOwnership)) {
        return true
    }
    return false
}