package net.corda.demobench.model

import tornadofx.ItemViewModel

class NodeDataModel : ItemViewModel<NodeData>(NodeData()) {

    val legalName = bind { item?.legalName }
    val nearestCity = bind { item?.nearestCity }
    val messagingPort = bind { item?.messagingPort }
    val rpcPort = bind { item?.rpcPort }
    val webPort = bind { item?.webPort }
    val h2Port = bind { item?.h2Port }

}
