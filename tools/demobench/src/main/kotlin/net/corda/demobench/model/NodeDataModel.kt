package net.corda.demobench.model

import tornadofx.*

class NodeDataModel : ItemViewModel<NodeData>(NodeData()) {

    val legalName = bind { item?.legalName }
    val nearestCity = bind { item?.nearestCity }
    val p2pPort = bind { item?.p2pPort }
    val rpcPort = bind { item?.rpcPort }
    val webPort = bind { item?.webPort }
    val h2Port = bind { item?.h2Port }

}
