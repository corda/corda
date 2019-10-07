package net.corda.node.internal

import net.corda.node.utilities.errorAndTerminate
import org.slf4j.LoggerFactory
import java.sql.SQLException

/**
 *
 * Cater for all type of unrecoverable [VirtualMachineError] in which the node may end up in an inconsistent state.
 * Fail fast and hard.
 */
class GeneralExceptionHandler(private val parentHandler: Thread.UncaughtExceptionHandler? = null) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(t: Thread?, e: Throwable?) {

        // fail fast with minimal overhead and further processing
        if (e is VirtualMachineError) {
            System.err.println("${e.message}")
            Runtime.getRuntime().halt(1)
        }
        // the error is a database connection issue - pull the rug
        else if (e is Error && e.cause is SQLException) {
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