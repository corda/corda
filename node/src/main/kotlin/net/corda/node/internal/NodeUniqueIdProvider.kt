/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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