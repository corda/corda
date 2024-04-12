package net.corda.nodeapi.internal

enum class NodeStatus {
    WAITING_TO_START,
    STARTING,
    STARTED,
    STOPPING
}
