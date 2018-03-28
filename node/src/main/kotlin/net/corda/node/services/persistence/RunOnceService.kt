/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.persistence

import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor
import net.corda.node.utilities.AffinityExecutor
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import org.hibernate.Session
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoField
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.persistence.*

const val TABLE = "${NODE_DATABASE_PREFIX}mutual_exclusion"
const val ID = "mutual_exclusion_id"
const val MACHINE_NAME = "machine_name"
const val PID = "pid"
const val TIMESTAMP = "mutual_exclusion_timestamp"
const val VERSION = "version"

/**
 * Makes sure only one node is able to write to database.
 * Running node updates row whilst running. When a node starts up, it checks no one has updated row within a specified time frame.
 *
 * @property machineName unique name for node.
 * @property pid process id.
 * @property updateInterval rate(milliseconds) at which the running node updates row.
 * @property waitInterval amount of time(milliseconds) to wait since last row update before being able to become the master node.
 * @property updateExecutor runs a row update every [updateInterval] milliseconds.
 */
class RunOnceService(private val database: CordaPersistence, private val machineName: String, private val pid: String,
                     private val updateInterval: Long, private val waitInterval: Long,
                     private val updateExecutor: ScheduledExecutorService =
                     AffinityExecutor.ServiceAffinityExecutor("RunOnceService", 1)) : SingletonSerializeAsToken() {

    private val log = loggerFor<RunOnceService>()
    private val running = AtomicBoolean(false)

    init {
        if (waitInterval <= updateInterval) {
            throw RuntimeException("Configuration Error: Node must wait longer than update rate otherwise someone else might be running!" +
                    " Wait interval: $waitInterval, Update interval: $updateInterval")
        }
    }

    @Entity
    @Table(name = TABLE)
    class MutualExclusion(machineNameInit: String, pidInit: String, timeStampInit: LocalDateTime, versionInit: Long = 0) {
        @Column(name = ID, insertable = false, updatable = false)
        @Id
        val id: Char = 'X'

        @Column(name = MACHINE_NAME)
        val machineName = machineNameInit

        @Column(name = PID)
        val pid = pidInit

        @Column(name = TIMESTAMP)
        val timestamp = timeStampInit

        @Column(name = VERSION)
        val version = versionInit
    }

    fun start() {
        database.transaction {
            val mutualExclusion = getMutualExclusion(session)

            when {
                mutualExclusion == null -> {
                    log.info("No other node running before")
                    insertMutualExclusion(session)
                }
                mutualExclusion.machineName == machineName -> {
                    log.info("Node last run on same machine:$machineName")
                    updateTimestamp(session, mutualExclusion)
                }
                else -> {
                    log.info("Node last run on different machine:${mutualExclusion.machineName} PID: ${mutualExclusion.pid}. " +
                            "Now running on $machineName PID: $pid")
                    updateTimestamp(session, mutualExclusion)
                }
            }
        }

        updateExecutor.scheduleAtFixedRate({
            if (running.compareAndSet(false, true)) {
                try {
                    database.transaction {
                        val mutualExclusion = getMutualExclusion(session)

                        if (mutualExclusion == null) {
                            log.error("$machineName PID: $pid failed mutual exclusion update. " +
                                    "Expected to have a row in $TABLE table. " +
                                    "Check if another node is running")
                            System.exit(1)
                        } else if (mutualExclusion.machineName != machineName || mutualExclusion.pid != pid) {
                            log.error("Expected $machineName PID: $pid but was ${mutualExclusion.machineName} PID: ${mutualExclusion.pid}. " +
                                    "Check if another node is running")
                            System.exit(1)
                        }

                        updateTimestamp(session, mutualExclusion!!)
                    }
                } finally {
                    running.set(false)
                }
            }
        }, updateInterval, updateInterval, TimeUnit.MILLISECONDS)
    }

    private fun insertMutualExclusion(session: Session) {
        val query = session.createNativeQuery("INSERT INTO $TABLE VALUES ('X', :machineName, :pid, CURRENT_TIMESTAMP, :version)", MutualExclusion::class.java)
        query.unwrap(org.hibernate.SQLQuery::class.java).addSynchronizedEntityClass(MutualExclusion::class.java)
        query.setParameter("pid", pid)
        query.setParameter("machineName", machineName)
        query.setParameter("version", 0)
        val returnValue = query.executeUpdate()

        if (returnValue != 1) {
            log.error("$machineName PID: $pid failed to insert mutual exclusion. Check if another node is running")
            System.exit(1)
        }
    }

    private fun getMutualExclusion(session: Session): MutualExclusion? {
        val query = session.createNativeQuery("SELECT * FROM $TABLE WHERE $ID='X'", MutualExclusion::class.java)
        val result = query.resultList.singleOrNull()
        return if (result != null) result as MutualExclusion else null
    }

    private fun updateTimestamp(session: Session, mutualExclusion: MutualExclusion): Boolean {

        val hql = "UPDATE RunOnceService\$MutualExclusion SET $MACHINE_NAME = :machineName, $TIMESTAMP = CURRENT_TIMESTAMP, $PID = :pid,  $VERSION = :newVersion " +
                "WHERE $ID = 'X' AND " +
                // we are master node
                "($MACHINE_NAME = :machineName OR " +
                // change master node
                "($MACHINE_NAME != :machineName AND " +
                // no one else has updated timestamp whilst we attempted this update
                "$VERSION = :oldVersion  AND " +
                // old timestamp
                "CAST(CURRENT_TIMESTAMP as LocalDateTime) > CAST(:waitTime as LocalDateTime)))"

        val query = session.createQuery(hql)

        val oldVersion = mutualExclusion.version
        val minWaitTime = mutualExclusion.timestamp.plus(waitInterval, ChronoField.MILLI_OF_SECOND.baseUnit)

        query.setParameter("pid", pid)
        query.setParameter("machineName", machineName)
        query.setParameter("oldVersion", oldVersion)
        query.setParameter("newVersion", oldVersion+1)
        query.setParameter("waitTime", minWaitTime)
        val returnValue = query.executeUpdate()

        if (returnValue != 1) {
            if (machineName == mutualExclusion.machineName) {
                log.error("$machineName PID: $pid failed mutual exclusion update. Check if another node is running")
            } else {
                log.error("$machineName PID: $pid failed to become the master node. " +
                        "Check if ${mutualExclusion.machineName}, PID: ${mutualExclusion.pid} is still running. " +
                        "Try again in ${Duration.ofMillis(waitInterval)}")
            }
            System.exit(1)
        }

        return returnValue == 1
    }
}
