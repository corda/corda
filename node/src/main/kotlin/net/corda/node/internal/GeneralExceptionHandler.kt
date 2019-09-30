package net.corda.node.internal

import net.corda.node.utilities.errorAndTerminate
import org.slf4j.LoggerFactory

/**
 *
 * Cater for all type of unrecoverable [VirtualMachineError] in which the node may end up in an inconsistent state.
 * Fail fast and hard.
 */
const val UNRECOVERABLE_ERROR = "Thread failed due to an unrecoverable Virtual Machine error. Terminating node."

class GeneralExceptionHandler(private val parentHandler: Thread.UncaughtExceptionHandler? = null) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(t: Thread?, e: Throwable?) {

        // fail fast with minimal overhead and further processing
        if (e is VirtualMachineError) {
            errorAndTerminate(UNRECOVERABLE_ERROR, e)
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