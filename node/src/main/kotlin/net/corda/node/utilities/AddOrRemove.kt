/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.utilities

import net.corda.core.serialization.CordaSerializable

/**
 * Enum for when adding/removing something, for example adding or removing an entry in a directory.
 */
@CordaSerializable
enum class AddOrRemove {
    ADD,
    REMOVE
}
