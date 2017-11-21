package com.r3.enclaves

@Suppress("unused")
class DummySystemClassLoader(parent: ClassLoader) : ClassLoader(parent) {
    fun startBlacklisting() {}
}
