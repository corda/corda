package com.r3.enclaves

@Suppress("unused", "unused_parameter")
class DummySystemClassLoader(parent: ClassLoader) : ClassLoader(parent) {
    fun startBlacklisting(t: Thread) {}
}
