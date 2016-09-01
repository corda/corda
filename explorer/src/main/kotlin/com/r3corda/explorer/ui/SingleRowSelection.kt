package com.r3corda.explorer.ui

sealed class SingleRowSelection<A> {
    class None<A> : SingleRowSelection<A>()
    class Selected<A>(val node: A) : SingleRowSelection<A>()
}
