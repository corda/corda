package net.corda.demobench.model

import tornadofx.ItemViewModel

class NodeDataModel : ItemViewModel<NodeData>(NodeData()) {

    val legalName = bind { item?.legalName }
    val p2pPort = bind { item?.p2pPort }
    val artemisPort = bind { item?.artemisPort }
    val webPort = bind { item?.webPort }

}
