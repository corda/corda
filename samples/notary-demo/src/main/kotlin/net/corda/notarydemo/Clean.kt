package net.corda.notarydemo

import net.corda.testing.internal.demorun.clean

fun main(args: Array<String>) {
    listOf(SingleNotaryCordform, RaftNotaryCordform, BFTNotaryCordform).forEach {
        it.clean()
    }
}
