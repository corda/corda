package net.corda.notarydemo

import net.corda.testing.node.internal.demorun.nodeRunner

fun main(args: Array<String>) {
    listOf(SingleNotaryCordform(), RaftNotaryCordform(), BFTNotaryCordform()).map { it.nodeRunner() }.forEach {
        it.clean()
    }
}
