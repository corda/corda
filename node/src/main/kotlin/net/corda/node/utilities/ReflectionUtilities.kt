package net.corda.node.utilities

fun <T> Class<T>.defaultOrNewInstance(): T {
    val kclazz = this::class
    return if (kclazz.objectInstance == null) {
//        kclazz.createInstance()
        this.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
    }
    else {
//        kclazz.objectInstance
        this.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
    }
}