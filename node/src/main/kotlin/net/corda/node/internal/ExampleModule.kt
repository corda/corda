package net.corda.node.internal

import com.google.inject.AbstractModule
import com.google.inject.Provides
import javax.inject.Qualifier


@Qualifier
annotation class SomeInteger

@Qualifier
annotation class AnotherInteger


class QualifierExampleModule : AbstractModule() {

    override fun configure() {
        /**
         * This will 'match' an "@SomeInteger i: Int"
         */
        bind(Int::class.java).annotatedWith(SomeInteger::class.java).to(14)
    }

    /**
     * Same as above, different syntax.
     */
    @Provides
    @AnotherInteger
    fun provideAnoterInteger(): Int = 34
}
