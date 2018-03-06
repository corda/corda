/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.testing.node

/**
 * Object encapsulating a node rpc user and their associated permissions for use when testing using the [driver]
 *
 * @property username The rpc user's username
 * @property password The rpc user's password
 * @property permissions A [List] of [String] detailing the [User]'s permissions
 * */
data class User(
        val username: String,
        val password: String,
        val permissions: Set<String>)