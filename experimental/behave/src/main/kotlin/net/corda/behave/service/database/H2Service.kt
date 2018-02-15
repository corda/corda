package net.corda.behave.service.database

import net.corda.behave.service.Service

class H2Service(
        name: String,
        port: Int
) : Service(name, port) {

    companion object {

        val database = "node"
        val schema = "dbo"
        val username = "sa"

    }

}