package com.r3.corda.networkmanage.common.persistence

import net.corda.core.node.NodeInfo
import net.corda.nodeapi.internal.SignedNodeInfo

class NodeInfoWithSigned(val signedNodeInfo: SignedNodeInfo) {
    val nodeInfo: NodeInfo = signedNodeInfo.verified()
}