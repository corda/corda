/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.node.configuration

class UserConfiguration : ConfigurationTemplate(), Iterable<UserConfiguration.User> {

    data class User(val username: String, val password: String, val permissions: List<String>)

    private val users = mutableListOf<User>()

    fun withUser(username: String, password: String, permissions: List<String> = listOf("ALL")): UserConfiguration {
        users.add(User(username, password, permissions))
        return this
    }

    override fun iterator(): Iterator<User> {
        return users.iterator()
    }

    override val config: (Configuration) -> String
        get() = {
            """
            |rpcUsers=[
            |${users.joinToString("\n") { userObject(it) }}
            |]
            """
        }

    private fun userObject(user: User): String {
        return """
            |{
            |  username="${user.username}"
            |  password="${user.password}"
            |  permissions=[${user.permissions.joinToString(", ")}]
            |}
            """.trimMargin()
    }

}
