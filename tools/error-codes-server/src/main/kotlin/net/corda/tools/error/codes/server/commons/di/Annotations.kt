package net.corda.tools.error.codes.server.commons.di

import javax.inject.Qualifier

@Qualifier
@MustBeDocumented
@Target(AnnotationTarget.CLASS, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class FunctionQualifier(val origin: String)