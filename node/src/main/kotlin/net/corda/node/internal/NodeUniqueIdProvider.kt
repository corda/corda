package net.corda.node.internal

interface NodeUniqueIdProvider {
    val value: String
}

// this is stubbed because we still do not support clustered node setups.
// the moment we will, this will have to be changed to return a value unique for each physical node.
internal object StubbedNodeUniqueIdProvider : NodeUniqueIdProvider {

    // TODO implement to return a value unique for each physical node when we will support clustered node setups.
    override val value: String = "NABOB"
}