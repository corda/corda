package net.corda.node.internal.cordapp

import net.corda.core.cordapp.Cordapp
import net.corda.core.cordapp.CordappProvider

interface CordappProviderInternal : CordappProvider {
    val cordapps: List<Cordapp>
}
