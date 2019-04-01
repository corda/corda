package net.corda.node.internal

import net.corda.node.utilities.errorAndTerminate
import org.slf4j.LoggerFactory
import java.sql.SQLException

/**
 * If a thread dies because it can't connect to the database, the node ends up in an inconsistent state.
 * Fail fast and hard.
 */
class DbExceptionHandler(private val parentHandler: Thread.UncaughtExceptionHandler? = null) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(t: Thread?, e: Throwable?) {

        // the error is a database connection issue - pull the rug
        if (e is Error && e.cause is SQLException) {
            errorAndTerminate("Thread ${t!!.name} failed due to database connection error. This is unrecoverable, terminating node.", e)
        }

        // replicate the default error handling from ThreadGroup for all other unhandled exceptions
        if (parentHandler != null) {
            parentHandler.uncaughtException(t, e)
        } else if (e !is ThreadDeath) {
            System.err.print("Exception in thread \"" + t!!.getName() + "\" ")
            e!!.printStackTrace(System.err)
            LoggerFactory.getLogger(this.javaClass.name).error("Exception in thread \"" + t.getName() + "\"", e)
        }
    }
}