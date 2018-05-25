/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.persistence

import co.paralleluniverse.strands.Strand
import org.hibernate.Session
import org.hibernate.Transaction
import rx.subjects.PublishSubject
import java.sql.Connection
import java.util.*

fun currentDBSession(): Session = contextTransaction.session
private val _contextTransaction = ThreadLocal<DatabaseTransaction>()
var contextTransactionOrNull: DatabaseTransaction?
    get() = _contextTransaction.get()
    set(transaction) = _contextTransaction.set(transaction)
val contextTransaction get() = contextTransactionOrNull ?: error("Was expecting to find transaction set on current strand: ${Strand.currentStrand()}")

class DatabaseTransaction(
        isolation: Int,
        val outerTransaction: DatabaseTransaction?,
        val database: CordaPersistence
) {
    val id: UUID = UUID.randomUUID()

    private var _connectionCreated = false
    val connectionCreated get() = _connectionCreated
    val connection: Connection by lazy(LazyThreadSafetyMode.NONE) {
        database.dataSource.connection
                .apply {
                    _connectionCreated = true
                    // only set the transaction isolation level if it's actually changed - setting isn't free.
                    if (transactionIsolation != isolation) {
                        transactionIsolation = isolation
                    }
                }
    }

    private val sessionDelegate = lazy {
        val session = database.entityManagerFactory.withOptions().connection(connection).openSession()
        hibernateTransaction = session.beginTransaction()
        session
    }

    val session: Session by sessionDelegate
    private lateinit var hibernateTransaction: Transaction

    internal val boundary = PublishSubject.create<CordaPersistence.Boundary>()
    private var committed = false

    fun commit() {
        if (sessionDelegate.isInitialized()) {
            hibernateTransaction.commit()
        }
        if (_connectionCreated) {
            connection.commit()
        }
        committed = true
    }

    fun rollback() {
        if (sessionDelegate.isInitialized() && session.isOpen) {
            session.clear()
        }
        if (_connectionCreated && !connection.isClosed) {
            connection.rollback()
        }
    }

    fun close() {
        if (sessionDelegate.isInitialized() && session.isOpen) {
            session.close()
        }
        if (_connectionCreated) {
            connection.close()
        }
        contextTransactionOrNull = outerTransaction
        if (outerTransaction == null) {
            boundary.onNext(CordaPersistence.Boundary(id, committed))
        }
    }

    fun onCommit(callback: () -> Unit) {
        boundary.filter { it.success }.subscribe { callback() }
    }

    fun onRollback(callback: () -> Unit) {
        boundary.filter { !it.success }.subscribe { callback() }
    }
}
