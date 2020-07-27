package net.corda.node.services.network

import net.corda.core.node.NotaryInfo

interface NotaryListUpdateListener {
    fun onNewNotaryList(notaries: List<NotaryInfo>)
}