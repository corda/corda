package net.corda.bn.flows

import net.corda.bn.flows.extensions.BNMemberAuth
import net.corda.bn.flows.extensions.DecentralisedBNMemberAuth

object BNUtils {
    fun loadBNMemberAuth(): BNMemberAuth = DecentralisedBNMemberAuth()
}