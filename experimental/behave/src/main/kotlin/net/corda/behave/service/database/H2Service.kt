package net.corda.behave.service.database

import net.corda.behave.service.Service

class H2Service(
        name: String,
        port: Int
) : Service(name, port) {

    companion object {

        const val database = "node"
        const val schema = "dbo"
        const val username = "sa"
    }
}