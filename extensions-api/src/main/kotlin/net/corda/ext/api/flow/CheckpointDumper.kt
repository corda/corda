package net.corda.ext.api.flow

/**
 * Allows to dump (i.e. store) checkpoints currently in-flight to an external media.
 */
interface CheckpointDumper {
    fun dumpCheckpoints()
}