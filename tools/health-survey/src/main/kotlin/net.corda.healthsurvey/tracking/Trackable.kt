package net.corda.healthsurvey.tracking

interface Trackable {

    fun step(description: String)

    fun complete(description: String)

    fun fail(description: String)

    fun run()

}