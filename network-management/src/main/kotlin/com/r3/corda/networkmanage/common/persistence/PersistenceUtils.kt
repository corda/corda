package com.r3.corda.networkmanage.common.persistence

import net.corda.node.utilities.DatabaseTransaction
import javax.persistence.LockModeType
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.Path
import javax.persistence.criteria.Predicate

fun <T> DatabaseTransaction.singleRequestWhere(clazz: Class<T>, predicate: (CriteriaBuilder, Path<T>) -> Predicate): T? {
    val builder = session.criteriaBuilder
    val criteriaQuery = builder.createQuery(clazz)
    val query = criteriaQuery.from(clazz).run {
        criteriaQuery.where(predicate(builder, this))
    }
    return session.createQuery(query).setLockMode(LockModeType.PESSIMISTIC_WRITE).resultList.firstOrNull()
}

fun <T> DatabaseTransaction.deleteRequest(clazz: Class<T>, predicate: (CriteriaBuilder, Path<T>) -> Predicate): Int {
    val builder = session.criteriaBuilder
    val criteriaDelete = builder.createCriteriaDelete(clazz)
    val delete = criteriaDelete.from(clazz).run {
        criteriaDelete.where(predicate(builder, this))
    }
    return session.createQuery(delete).executeUpdate()
}

